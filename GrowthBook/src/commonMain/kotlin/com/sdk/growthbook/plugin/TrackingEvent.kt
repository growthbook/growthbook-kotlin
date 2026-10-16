package com.sdk.growthbook.plugin

import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
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
 *
 * Internal: [payload] is a `kotlinx.serialization` type, and the SDK does not expose those in its
 * public API. Nothing public ever accepted or returned this class, so it stays where it belongs —
 * inside the plugin's wire path. The event names callers may legitimately need are published
 * separately as [GBTrackingEventNames].
 */
internal data class TrackingEvent(
    val payload: JsonObject,
    val dedupeKey: String? = null,
) {
    companion object {
        const val EVENT_EXPERIMENT_VIEWED = GBTrackingEventNames.EXPERIMENT_VIEWED
        const val EVENT_FEATURE_EVALUATED = GBTrackingEventNames.FEATURE_EVALUATED

        private val TOP_LEVEL_ATTR_KEYS = setOf(
            "user_id", "device_id", "anonymous_id", "id", "page_id", "session_id", "utmCampaign",
            "utmContent", "utmMedium", "utmSource", "utmTerm", "pageTitle"
        )

        /**
         * `Experiment Viewed` properties, as data rather than JSON: the same map is handed to a
         * consumer's event filter, to [com.sdk.growthbook.GBEventLogger] via [GBEventDispatch],
         * and then serialized into the payload — so a filter or a sink can never see something
         * other than what is sent. Insertion order is preserved, which keeps the serialized form —
         * and therefore the de-duplication key — stable.
         */
        fun experimentProperties(
            experiment: GBExperiment,
            result: GBExperimentResult
        ): Map<String, GBValue> = buildMap {
            put("experimentId", GBString(experiment.key))
            put("variationId", GBString(result.key))
            result.hashAttribute?.let { put("hashAttribute", GBString(it)) }
            result.hashValue?.let { put("hashValue", GBString(it)) }
            // Contextual-bandit attribution. The TS plugin does not send these (yet) —
            // additive extra properties, populated only for enrolled bandit exposures,
            // so non-bandit events are byte-identical to the TS shape.
            result.leafId?.let { put("leafId", GBNumber(it)) }
            result.variationWeights?.let { weights ->
                put("variationWeights", GBArray(weights.map { GBNumber(it) }))
            }
            result.banditVersion?.let { put("banditVersion", GBNumber(it)) }
        }

        /** `Feature Evaluated` properties; see [experimentProperties] for why this is a map. */
        fun featureProperties(
            featureKey: String,
            result: GBFeatureResult
        ): Map<String, GBValue> = buildMap {
            put("feature", GBString(featureKey))
            put("source", GBString(result.source.name))
            result.gbValue?.let { put("value", it) }
            put("ruleId", GBString(featureRuleId(result)))
            put("variationId", GBString(result.experimentResult?.key ?: ""))
        }

        /**
         * Serializes [properties] into the wire payload.
         *
         * Whether the event gets a [dedupeKey] follows from [eventName], exactly as in the
         * reference JS plugin: only the SDK's own two events are de-duplicated, because a caller
         * who logs the same custom event twice means it twice. Deriving it here rather than taking
         * it as a parameter keeps the rule in one place and keeps parity even when a caller names
         * a custom event after one of ours.
         */
        fun from(
            eventName: String,
            properties: Map<String, GBValue>,
            attributes: Map<String, GBValue>?
        ): TrackingEvent {
            val propertiesJson = buildJsonObject {
                properties.forEach { (key, value) -> put(key, value.gbSerialize()) }
            }
            val dedupeKey =
                if (eventName == EVENT_FEATURE_EVALUATED || eventName == EVENT_EXPERIMENT_VIEWED) {
                    buildJsonObject {
                        put("eventName", eventName)
                        put("properties", propertiesJson)
                    }.toString()
                } else {
                    null
                }
            return TrackingEvent(buildPayload(eventName, propertiesJson, attributes), dedupeKey)
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
