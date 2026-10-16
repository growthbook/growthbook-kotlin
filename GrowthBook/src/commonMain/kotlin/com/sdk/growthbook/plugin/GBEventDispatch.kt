package com.sdk.growthbook.plugin

import com.sdk.growthbook.GBEventLogger
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.PluginRegistry

/**
 * Single point where an event fans out to its sinks: the typed [PluginRegistry] callbacks first,
 * then the structured [GBEventLogger], which receives every event the SDK produces —
 * `Experiment Viewed`, `Feature Evaluated` and explicit `logEvent(...)` calls — under the names in
 * [GBTrackingEventNames] with the property maps the other GrowthBook SDKs use.
 *
 * Stateless on purpose, so evaluators stay stateless and the fan-out rules live in one file rather
 * than at each evaluation site. The reference JS SDK gets the same result by making the event
 * logger the *only* sink and having plugins register themselves into it; that shape does not port
 * here (a plugin has no handle on the SDK instance, plugins are a list rather than a single
 * overwritable slot, and the typed callbacks are worth keeping for Swift). What consumers observe
 * — event names, property keys, ordering — is identical either way.
 *
 * Every user-supplied callback is guarded: a throwing sink is logged and swallowed so analytics can
 * never break evaluation.
 */
internal object GBEventDispatch {

    fun experimentViewed(
        plugins: PluginRegistry?,
        eventLogger: GBEventLogger?,
        experiment: GBExperiment,
        result: GBExperimentResult,
        attributes: Map<String, GBValue>?,
    ) {
        plugins?.fireExperimentViewed(experiment, result, attributes)

        // Nothing below runs unless a logger is registered — the property map is an allocation on
        // an evaluation path, so it is built only when something will actually read it.
        val logger = eventLogger ?: return
        emit(
            logger,
            GBTrackingEventNames.EXPERIMENT_VIEWED,
            TrackingEvent.experimentProperties(experiment, result),
            attributes,
        )
    }

    fun featureEvaluated(
        plugins: PluginRegistry?,
        eventLogger: GBEventLogger?,
        featureKey: String,
        result: GBFeatureResult,
        attributes: Map<String, GBValue>?,
    ) {
        plugins?.fireFeatureEvaluated(featureKey, result, attributes)

        val logger = eventLogger ?: return
        emit(
            logger,
            GBTrackingEventNames.FEATURE_EVALUATED,
            TrackingEvent.featureProperties(featureKey, result),
            attributes,
        )
    }

    /**
     * Fans out an explicit [com.sdk.growthbook.GrowthBookSDK.logEvent] call. Properties come from
     * the caller as-is: unlike the SDK's own events there is no shape to impose on them.
     */
    fun customEvent(
        plugins: PluginRegistry?,
        eventLogger: GBEventLogger?,
        eventName: String,
        properties: Map<String, GBValue>,
        attributes: Map<String, GBValue>?,
    ) {
        plugins?.fireCustomEvent(eventName, properties, attributes)
        eventLogger?.let { emit(it, eventName, properties, attributes) }
    }

    private fun emit(
        logger: GBEventLogger,
        eventName: String,
        properties: Map<String, GBValue>,
        attributes: Map<String, GBValue>?,
    ) {
        try {
            logger(eventName, properties, attributes)
        } catch (t: Throwable) {
            // Name only — properties and attributes are consumer data and must never reach the log.
            GB.warning("GrowthBook: eventLogger failed for '$eventName': $t")
        }
    }
}

/**
 * Evaluation-site shorthands. They exist so an evaluator names the event and nothing else: which
 * sinks are attached and what identity rides along is [GBEventDispatch]'s business.
 */
internal fun EvaluationContext.fireExperimentViewed(
    experiment: GBExperiment,
    result: GBExperimentResult,
) = GBEventDispatch.experimentViewed(
    plugins = pluginRegistry,
    eventLogger = eventLogger,
    experiment = experiment,
    result = result,
    attributes = userContext.attributes,
)

internal fun EvaluationContext.fireFeatureEvaluated(
    featureKey: String,
    result: GBFeatureResult,
) = GBEventDispatch.featureEvaluated(
    plugins = pluginRegistry,
    eventLogger = eventLogger,
    featureKey = featureKey,
    result = result,
    attributes = userContext.attributes,
)
