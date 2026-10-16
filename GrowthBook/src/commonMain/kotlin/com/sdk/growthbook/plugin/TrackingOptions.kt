package com.sdk.growthbook.plugin

import com.sdk.growthbook.network.TrackingNetworkDispatcher
import kotlin.time.Duration

/**
 * Fully resolved configuration of [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin] —
 * every value with its default already applied.
 *
 * Internal on purpose: this is where a new plugin option goes. Nothing outside the SDK is bound to
 * this signature, so adding a parameter here is neither source- nor binary-breaking. The public way
 * in is [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.Builder], whose fluent setters
 * are additive by construction, while the public [TrackingPluginConfig] stays frozen at the options
 * it shipped with. That keeps the plugin's configuration growable without a major version bump —
 * and without the telescoping constructors a public config class would otherwise accumulate.
 */
internal data class TrackingOptions(
    val ingestorHost: String,
    val clientKey: String?,
    val batchSize: Int,
    val batchTimeout: Duration,
    val networkDispatcher: TrackingNetworkDispatcher?,
    val dedupeCacheSize: Int,
    val enableFeatureUsageEvents: Boolean,
    val dedupeKeyAttributes: List<String>,
    val enable: Boolean,
    val eventFilter: ((GBTrackingEventData) -> Boolean)?
)

/**
 * Adapts the frozen public config to [TrackingOptions]. Options introduced after [TrackingPluginConfig]
 * was frozen cannot be expressed by it, so they take their default here; set them via
 * [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.Builder] instead.
 *
 * Suppressed rather than migrated: adapting the deprecated type is this function's whole job.
 */
@Suppress("DEPRECATION")
internal fun TrackingPluginConfig.toTrackingOptions(): TrackingOptions = TrackingOptions(
    ingestorHost = resolvedIngestorHost(),
    clientKey = clientKey,
    batchSize = resolvedBatchSize(),
    batchTimeout = resolvedBatchTimeout(),
    networkDispatcher = networkDispatcher,
    dedupeCacheSize = resolvedDedupeCacheSize(),
    enableFeatureUsageEvents = true,
    dedupeKeyAttributes = emptyList(),
    enable = true,
    eventFilter = null
)

/**
 * Single source of truth for turning a caller's null/out-of-range value into the option actually
 * used. Shared by [TrackingPluginConfig] and
 * [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.Builder] so the two entry points can
 * never drift apart.
 *
 * The default values still live on the deprecated [TrackingPluginConfig] because they are public
 * API there; reading them is deliberate, hence the suppression.
 */
@Suppress("DEPRECATION")
internal object TrackingOptionDefaults {

    fun ingestorHost(value: String?): String =
        if (value.isNullOrEmpty()) {
            TrackingPluginConfig.DEFAULT_INGESTOR_HOST
        } else {
            value.removeSuffix("/")
        }

    fun batchSize(value: Int?): Int =
        if (value == null || value <= 0) TrackingPluginConfig.DEFAULT_BATCH_SIZE else value

    fun batchTimeout(value: Duration?): Duration =
        if (value == null || value <= Duration.ZERO) TrackingPluginConfig.DEFAULT_BATCH_TIMEOUT else value

    fun dedupeCacheSize(value: Int?): Int =
        if (value == null || value <= 0) TrackingPluginConfig.DEFAULT_DEDUPE_CACHE_SIZE else value
}
