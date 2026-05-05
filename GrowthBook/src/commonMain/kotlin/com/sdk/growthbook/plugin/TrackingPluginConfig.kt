package com.sdk.growthbook.plugin

import com.sdk.growthbook.network.TrackingNetworkDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin]. Defaults: batch size 100, flush every 10 seconds,
 * ingestor host `https://us1.gb-ingest.com`.
 */
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
) {
    fun resolvedIngestorHost(): String {
        if (ingestorHost.isNullOrEmpty()) {
            return DEFAULT_INGESTOR_HOST
        }
        return stripTrailingSlash(ingestorHost)
    }

    fun resolvedBatchSize(): Int {
        return if (batchSize == null || batchSize <= 0) {
            DEFAULT_BATCH_SIZE
        } else {
            batchSize
        }
    }

    fun resolvedBatchTimeout(): Duration {
        if (batchTimeout == null || batchTimeout <= Duration.ZERO) {
            return DEFAULT_BATCH_TIMEOUT
        } else {
            return batchTimeout
        }
    }

    companion object {
        const val DEFAULT_INGESTOR_HOST = "https://us1.gb-ingest.com"
        const val DEFAULT_BATCH_SIZE = 100
        val DEFAULT_BATCH_TIMEOUT: Duration = 10.seconds

        private fun stripTrailingSlash(str: String) = str.removeSuffix("/")
    }
}
