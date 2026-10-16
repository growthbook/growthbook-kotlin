package com.sdk.growthbook.plugin

import com.sdk.growthbook.model.GBValue

/**
 * A tracking event as seen by
 * [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.Builder.setEventFilter], before it is
 * serialized and queued. Mirrors the TS plugin's `EventData`.
 *
 * [properties] is exactly what would be sent as `properties_json` and [attributes] exactly what
 * would populate `context_json` and the promoted identity fields, so a filter decides on the real
 * contents of the event rather than an approximation. Values are [GBValue]s: the payload's JSON form
 * is an internal detail, and the SDK keeps `kotlinx.serialization` types out of its public API.
 *
 * Compare [eventName] against [GBTrackingEventNames] to branch on the SDK's own events.
 *
 * Instances arrive in the filter; the constructor is internal so the SDK can add fields here without
 * breaking anyone, which a public constructor would make impossible (see [TrackingPluginConfig] for
 * where that road leads). Opening it later is additive and safe, so it starts closed. Not a `data`
 * class either: a generated `toString()` would print [attributes], i.e. user data, into whatever log
 * happened to touch it.
 */
class GBTrackingEventData internal constructor(
    val eventName: String,
    val properties: Map<String, GBValue>,
    val attributes: Map<String, GBValue>?,
    /**
     * Page URL the event belongs to. Always empty on this SDK — there is no browser location to
     * read — and present for parity with the ingest payload, which carries a `url` field.
     */
    val url: String = "",
)
