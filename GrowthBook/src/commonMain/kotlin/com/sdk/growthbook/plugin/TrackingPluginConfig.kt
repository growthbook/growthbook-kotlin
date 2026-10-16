package com.sdk.growthbook.plugin

import com.sdk.growthbook.network.TrackingNetworkDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin]. Defaults: batch size 100, flush every 10 seconds,
 * ingestor host `https://us-east-1.gb-ingest.com`.
 *
 * **Frozen.** This class carries exactly the options it shipped with and will not gain more: it has
 * a public constructor, so every added parameter would break binary compatibility and leave behind
 * a constructor overload that has to be kept forever. New options live on
 * [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.Builder], which is the recommended
 * way to configure the plugin:
 *
 * ```kotlin
 * GrowthBookTrackingPlugin.Builder()
 *     .setClientKey("sdk-abc")
 *     .setNetworkDispatcher(dispatcher)
 *     .setEnableFeatureUsageEvents(false)
 *     .build()
 * ```
 *
 * Deprecated at [DeprecationLevel.WARNING]: passing this config to the plugin still works and is
 * unchanged at the bytecode level, so existing code keeps running and only warns at compile time.
 * It simply cannot express options introduced after it was frozen, which take their defaults
 * instead. Removal not before 10.0.0.
 */
@Deprecated(
    message = "Configure the plugin with GrowthBookTrackingPlugin.Builder(). " +
        "This class is frozen and will be removed no earlier than 10.0.0.",
    level = DeprecationLevel.WARNING,
)
data class TrackingPluginConfig(
    /** Base URL of the ingest endpoint. Events are POSTed to `{ingestorHost}/track`. */
    val ingestorHost: String? = null,
    /**
     * Client key (SDK connection key). If null/empty the plugin becomes a no-op — it will not
     * make HTTP requests but [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin.close] still completes cleanly.
     */
    val clientKey: String? = null,
    /** Max events buffered before an eager flush. */
    val batchSize: Int? = null,
    /** Max time an event sits in the buffer before a scheduled flush. */
    val batchTimeout: Duration? = null,
    /** Network dispatcher used to POST tracking events. */
    val networkDispatcher: TrackingNetworkDispatcher? = null,
    /**
     * Max number of recently-seen "Feature Evaluated"/"Experiment Viewed" events kept for
     * de-duplication. Repeated events with identical properties within this LRU window are dropped
     * before enqueueing (mirrors the JS tracking plugin). Defaults to [DEFAULT_DEDUPE_CACHE_SIZE].
     */
    val dedupeCacheSize: Int? = null,
) {
    fun resolvedIngestorHost(): String = TrackingOptionDefaults.ingestorHost(ingestorHost)

    fun resolvedBatchSize(): Int = TrackingOptionDefaults.batchSize(batchSize)

    fun resolvedBatchTimeout(): Duration = TrackingOptionDefaults.batchTimeout(batchTimeout)

    fun resolvedDedupeCacheSize(): Int = TrackingOptionDefaults.dedupeCacheSize(dedupeCacheSize)

    companion object {
        /**
         * Region-qualified ingest host, matching the reference SDK and the GrowthBook platform's
         * own default since growthbook#6608 — it replaced the legacy `us1.gb-ingest.com` alias.
         * Override it with the builder when your Data Region is not us-east-1, otherwise events are
         * dropped at the wrong cluster.
         */
        const val DEFAULT_INGESTOR_HOST = "https://us-east-1.gb-ingest.com"
        const val DEFAULT_BATCH_SIZE = 100
        const val DEFAULT_DEDUPE_CACHE_SIZE = 1000
        val DEFAULT_BATCH_TIMEOUT: Duration = 10.seconds
    }
}
