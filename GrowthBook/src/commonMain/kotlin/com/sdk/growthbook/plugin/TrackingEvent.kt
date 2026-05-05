package com.sdk.growthbook.plugin

import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.plugin.tracking.SdkMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A single event dispatched by [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin] to the GrowthBook ingest endpoint.
 */
@Serializable
data class TrackingEvent(
    @SerialName("event_name")
    val eventName: String,
    @SerialName("properties")
    val properties: JsonObject,
    @SerialName("attributes")
    val attributes: JsonElement? = null,
) {
    companion object {
        const val EVENT_EXPERIMENT_VIEWED = "Experiment Viewed"
        const val EVENT_FEATURE_EVALUATED = "Feature Evaluated"

        fun forExperiment(
            experiment: GBExperiment,
            result: GBExperimentResult,
            attributes: JsonElement? = null
        ): TrackingEvent = TrackingEvent(
            eventName = EVENT_EXPERIMENT_VIEWED,
            properties = buildJsonObject {
                put("experimentId", experiment.key)
                put("variationId", result.variationId)
            },
            attributes = mergeWithSdkAttrs(attributes),
        )

        fun forFeature(
            featureKey: String,
            result: GBFeatureResult,
            attributes: JsonElement? = null
        ): TrackingEvent = TrackingEvent(
            eventName = EVENT_FEATURE_EVALUATED,
            properties = buildJsonObject {
                put("feature", featureKey)
                result.gbValue?.gbSerialize()?.let { put("value", it) }
                put("source", result.source.name)
                result.ruleId?.takeIf { it.isNotEmpty() }?.let { put("ruleId", it) }
            },
            attributes = mergeWithSdkAttrs(attributes),
        )

        private fun mergeWithSdkAttrs(userAttrs: JsonElement?): JsonElement = buildJsonObject {
            put("sdk_language", SdkMetadata.LANGUAGE)
            put("sdk_version", SdkMetadata.VERSION)
            (userAttrs as? JsonObject)?.forEach { (k, v) -> put(k, v) }
        }
    }
}
