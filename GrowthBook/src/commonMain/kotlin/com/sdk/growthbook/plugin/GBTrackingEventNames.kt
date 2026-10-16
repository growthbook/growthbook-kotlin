package com.sdk.growthbook.plugin

/**
 * Names the SDK uses for the events it tracks automatically. They are part of the ingest contract
 * and identical across the GrowthBook SDKs, so a warehouse query or a custom plugin can rely on
 * them rather than repeating the strings.
 *
 * Published here rather than on the event class itself, which is internal: its payload is a
 * `kotlinx.serialization` type and the SDK keeps those out of its public API.
 */
object GBTrackingEventNames {

    /** Emitted once per unique hashAttribute/hashValue/experiment/variation combination. */
    const val EXPERIMENT_VIEWED = "Experiment Viewed"

    /** Emitted on feature evaluation, subject to de-duplication and `setEnableFeatureUsageEvents`. */
    const val FEATURE_EVALUATED = "Feature Evaluated"
}
