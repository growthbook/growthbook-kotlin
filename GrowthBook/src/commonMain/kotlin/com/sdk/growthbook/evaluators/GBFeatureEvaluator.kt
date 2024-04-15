package com.sdk.growthbook.evaluators

import com.sdk.growthbook.Utils.GBUtils
import com.sdk.growthbook.Utils.TrackData
import com.sdk.growthbook.Utils.toJsonElement
import com.sdk.growthbook.model.FeatureEvalContext
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Feature Evaluator Class
 * Takes Context and Feature Key
 * Returns Calculated Feature Result against that key
 */
internal class GBFeatureEvaluator {

    /**
     * Takes Context and Feature Key
     * Returns Calculated Feature Result against that key
     */
    fun evaluateFeature(
        context: GBContext,
        featureKey: String,
        attributeOverrides: Map<String, Any>,
        evalContext: FeatureEvalContext = FeatureEvalContext(
            id = featureKey,
            evaluatedFeatures = mutableSetOf()
        )
    ): GBFeatureResult {
        try {
            if (evalContext.evaluatedFeatures.contains(featureKey)) {
                return prepareResult(
                    value = null,
                    source = GBFeatureSource.cyclicPrerequisite
                )
            }
            evalContext.evaluatedFeatures.add(featureKey)

            val targetFeature: GBFeature = context.features.getValue(featureKey)

            // Loop through the feature rules (if any)
            val rules = targetFeature.rules
            if (!rules.isNullOrEmpty()) {

                ruleLoop@ for (rule in rules) {

                    if (rule.parentConditions != null) {
                        for (parentCondition in rule.parentConditions) {
                            val parentResult = evaluateFeature(
                                context = context,
                                featureKey = parentCondition.id,
                                attributeOverrides = attributeOverrides,
                                evalContext = evalContext
                            )
                            if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                                return prepareResult(
                                    value = null,
                                    source = GBFeatureSource.cyclicPrerequisite
                                )
                            }
                            val evalObj = parentResult.value?.let {
                                JsonObject(mapOf("value" to it))
                            } ?: JsonObject(emptyMap())

                            val evalCondition = GBConditionEvaluator().evalCondition(
                                attributes = evalObj,
                                conditionObj = parentCondition.condition
                            )

                            // blocking prerequisite eval failed: feature evaluation fails
                            if (!evalCondition) {
                                if (parentCondition.gate != false) {
                                    println("Feature blocked by prerequisite")
                                    return prepareResult(
                                        value = null,
                                        source = GBFeatureSource.prerequisite
                                    )
                                }
                                // non-blocking prerequisite eval failed: break out of parentConditions loop, jump to the next rule
                                continue@ruleLoop
                            }
                        }
                    }

                    if (rule.filters != null) {
                        if (GBUtils.isFilteredOut(
                                filters = rule.filters,
                                attributeOverrides = context.attributes,
                                context = context
                            )
                        ) {
                            // Skip rule because of filters
                            continue
                        }
                    }

                    if (rule.force != null) {

                        if (rule.condition != null && !GBConditionEvaluator().evalCondition(
                                attributes = getAttributes(
                                    context = context,
                                    attributeOverrides = attributeOverrides
                                ).toJsonElement(),
                                conditionObj = rule.condition
                            )
                        ) {
                            // Skip rule because of condition
                            continue
                        }

                        val gate1 = (context.stickyBucketService != null)
                        val gate2 = (rule.disableStickyBucketing == false)
                        val shouldFallbackAttributeBePassed = gate1 && gate2
                        if (!GBUtils.isIncludedInRollout(
                                seed = rule.seed ?: featureKey,
                                hashAttribute = rule.hashAttribute,
                                fallbackAttribute = if (shouldFallbackAttributeBePassed) rule.fallbackAttribute
                                else null,
                                range = rule.range,
                                coverage = rule.coverage,
                                hashVersion = rule.hashVersion,
                                context = context,
                                attributeOverrides = attributeOverrides
                            )
                        ) {
                            // Skip rule because user not included in rollout
                            continue
                        }

                        if (rule.tracks != null) {
                            rule.tracks.forEach { track: TrackData ->
                                if (!GBExperimentHelper().isTracked(
                                        experiment = track.experiment,
                                        result = track.result
                                    )
                                ) {
                                    context.trackingCallback(track.experiment, track.result)
                                }
                            }
                        }

                        return prepareResult(value = rule.force, source = GBFeatureSource.force)
                    } else {

                        val variation = rule.variations
                        if (variation != null) {
                            val exp = GBExperiment(
                                key = rule.key ?: featureKey,
                                variations = variation,
                                namespace = rule.namespace,
                                hashAttribute = rule.hashAttribute,
                                fallBackAttribute = rule.fallbackAttribute,
                                hashVersion = rule.hashVersion,
                                disableStickyBucketing = rule.disableStickyBucketing,
                                bucketVersion = rule.bucketVersion,
                                minBucketVersion = rule.minBucketVersion,
                                weights = rule.weights,
                                coverage = rule.coverage,
                                ranges = rule.ranges,
                                meta = rule.meta,
                                filters = rule.filters,
                                seed = rule.seed,
                                name = rule.name,
                                phase = rule.phase
                            )

                            val result = GBExperimentEvaluator().evaluateExperiment(
                                context = context,
                                experiment = exp,
                                attributeOverrides = attributeOverrides
                            )
                            if (result.inExperiment && (result.passthrough != true)) {
                                return prepareResult(
                                    value = result.value,
                                    source = GBFeatureSource.experiment,
                                    experiment = exp,
                                    experimentResult = result
                                )
                            }
                        } else {
                            continue
                        }
                    }
                }
            }

            // Return (value = defaultValue or null, source = defaultValue)
            return prepareResult(
                value = targetFeature.defaultValue,
                source = GBFeatureSource.defaultValue
            )
        } catch (exception: Exception) {
            // If the key doesn't exist in context.features, return immediately (value = null, source = unknownFeature).
            return prepareResult(value = null, source = GBFeatureSource.unknownFeature)
        }
    }

    /**
     * This is a helper method to create a FeatureResult object.
     * Besides the passed-in arguments, there are two derived values - on and off, which are just the value cast to booleans.
     */
    private fun prepareResult(
        value: JsonElement?,
        source: GBFeatureSource,
        experiment: GBExperiment? = null,
        experimentResult: GBExperimentResult? = null
    ): GBFeatureResult {

        val isFalse = value == null || value.toString() == "false" || value.toString()
            .isEmpty() || value.toString() == "0"


        return GBFeatureResult(
            value = value,
            on = !isFalse,
            off = isFalse,
            source = source,
            experiment = experiment,
            experimentResult = experimentResult
        )
    }

    private fun getAttributes(
        context: GBContext, attributeOverrides: Map<String, Any>,
    ): Map<String, Any> {
        context.attributes.toMutableMap().putAll(attributeOverrides)
        return context.attributes
    }
}