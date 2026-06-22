package com.sdk.growthbook.plugin

import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.SdkMetadata
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A single event dispatched by [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin] to the GrowthBook ingest endpoint.
 *
 * [payload] is the exact JSON sent on the wire (TS `EventPayload` shape). [dedupeKey] is internal —
 * non-null only for the auto-tracked feature/experiment events; it is never serialized.
 */
data class TrackingEvent(
    val payload: JsonObject,
    val dedupeKey: String? = null,
) {
    companion object {
        const val EVENT_EXPERIMENT_VIEWED = "Experiment Viewed"
        const val EVENT_FEATURE_EVALUATED = "Feature Evaluated"

        private val TOP_LEVEL_ATTR_KEYS = setOf(
            "user_id", "device_id", "anonymous_id", "id", "page_id", "session_id", "utmCampaign",
            "utmContent", "utmMedium", "utmSource", "utmTerm", "pageTitle"
        )

        fun forExperiment(
            experiment: GBExperiment,
            result: GBExperimentResult,
            attributes: Map<String, GBValue>? = null
        ): TrackingEvent {
            val properties = buildJsonObject {
                put("experimentId", experiment.key)
                put("variationId", result.key)
                result.hashAttribute?.let { put("hashAttribute", it) }
                result.hashValue?.let { put("hashValue", it) }
            }
            return build(EVENT_EXPERIMENT_VIEWED, properties, attributes)
        }

        fun forFeature(
            featureKey: String,
            result: GBFeatureResult,
            attributes: Map<String, GBValue>? = null
        ): TrackingEvent {
            val properties = buildJsonObject {
                put("feature", featureKey)
                put("source", result.source.name)
                result.gbValue?.gbSerialize()?.let { put("value", it) }
                put("ruleId", featureRuleId(result))
                put("variationId", result.experimentResult?.key ?: "")
            }
            return build(EVENT_FEATURE_EVALUATED, properties, attributes)
        }

        private fun build(
            eventName: String,
            properties: JsonObject,
            attributes: Map<String, GBValue>?
        ): TrackingEvent {
            val payload = buildPayload(eventName, properties, attributes)
            val dedupeKey = buildJsonObject {
                put("eventName", eventName)
                put("properties", properties)
            }.toString()
            return TrackingEvent(payload, dedupeKey)
        }

        private fun buildPayload(
            eventName: String,
            properties: JsonObject,
            attributes: Map<String, GBValue>?
        ): JsonObject {
            val attrs = attributes ?: emptyMap()
            val context = attrs.filterKeys { it !in TOP_LEVEL_ATTR_KEYS }

            return buildJsonObject {
                put("event_name", eventName)
                put("properties_json", properties)
                put("sdk_language", SdkMetadata.LANGUAGE)
                put("sdk_version", SdkMetadata.VERSION)
                put("url", "")
                put("context_json", GBJson(context).gbSerialize())
                putStringOrNull("user_id", attrs["user_id"])
                putStringOrNull(
                    "device_id",
                    attrs["device_id"] ?: attrs["anonymous_id"] ?: attrs["id"]
                )
                putStringOrNull("page_id", attrs["page_id"])
                putStringOrNull("session_id", attrs["session_id"])
                putStringIfPresent("utm_source", attrs["utmSource"])
                putStringIfPresent("utm_medium", attrs["utmMedium"])
                putStringIfPresent("utm_campaign", attrs["utmCampaign"])
                putStringIfPresent("utm_term", attrs["utmTerm"])
                putStringIfPresent("utm_content", attrs["utmContent"])
                putStringIfPresent("page_title", attrs["pageTitle"])
            }
        }

        // Always emit the key; null when the attribute is missing or not a string (matches JS/Python).
        private fun JsonObjectBuilder.putStringOrNull(key: String, value: GBValue?) {
            val str = (value as? GBString)?.value
            if (str != null) put(key, str) else put(key, JsonNull)
        }

        // Optional fields: omit entirely when absent (matches JS/Python).
        private fun JsonObjectBuilder.putStringIfPresent(key: String, value: GBValue?) {
            (value as? GBString)?.value?.let { put(key, it) }
        }

        private fun featureRuleId(result: GBFeatureResult): String =
            if (result.source == GBFeatureSource.defaultValue) "\$default"
            else result.ruleId ?: ""
    }
}
