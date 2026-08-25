package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.TrackingEvent
import com.sdk.growthbook.plugin.TrackingPluginConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlin.concurrent.Volatile

/**
 * Batches experiment/feature evaluation events and POSTs them to the GrowthBook ingest endpoint.
 *
 * A flush is triggered when either the buffer reaches [com.sdk.growthbook.plugin.TrackingPluginConfig.resolvedBatchSize]
 * or a timer fires after [com.sdk.growthbook.plugin.TrackingPluginConfig.resolvedBatchTimeout]. [close] schedules a final
 * flush of any remaining buffered events and then cancels the coroutine scope.
 *
 * If [com.sdk.growthbook.plugin.TrackingPluginConfig.clientKey] is null/empty the plugin becomes a no-op: event methods
 * return immediately, no HTTP traffic occurs, and [close] still completes cleanly.
 *
 * Limitation: this version auto-tracks feature and experiment evaluations only. Unlike the JS/Python
 * SDKs it does not yet route custom events (`logEvent`/`log_event`) through this pipeline; that is
 * tracked as a follow-up.
 */
class GrowthBookTrackingPlugin(
    private val config: TrackingPluginConfig,
    private val coroutineScope: CoroutineScope = CoroutineScope(TrackingDispatcher)
) : GrowthBookPlugin {

    private val disabled = config.clientKey.isNullOrEmpty()
    private val mutex = Mutex()
    private val buffer = mutableListOf<TrackingEvent>()

    /** LRU of recently-seen [TrackingEvent.dedupeKey]s; mirrors the JS plugin's de-dupe cache. */
    private val dedupeCache = LinkedHashSet<String>()
    private var pendingFlush: Job? = null

    @Volatile
    private var closed = false

    override fun init() {
        if (disabled) {
            GB.warning("GrowthBookTrackingPlugin disabled: clientKey is null or empty")
        }
    }

    override fun onExperimentViewed(
        experiment: GBExperiment,
        result: GBExperimentResult,
        attributes: Map<String, GBValue>?
    ) {
        if (closed || disabled) {
            return
        }
        enqueue(TrackingEvent.forExperiment(experiment, result, attributes))
    }

    override fun onFeatureEvaluated(
        featureKey: String,
        result: GBFeatureResult,
        attributes: Map<String, GBValue>?
    ) {
        if (disabled || closed) {
            return
        }
        enqueue(TrackingEvent.forFeature(featureKey, result, attributes))
    }

    override fun close() {
        if (closed) return
        closed = true
        coroutineScope.launch {
            val toFlush = mutex.withLock {
                pendingFlush?.cancel()
                buffer.toList().also { buffer.clear() }
            }
            if (toFlush.isNotEmpty()) flushBatch(toFlush)
        }.invokeOnCompletion {
            coroutineScope.cancel()
        }
    }

    private fun enqueue(event: TrackingEvent) {
        coroutineScope.launch {
            val toFlush = mutex.withLock {
                // De-dupe "Feature Evaluated"/"Experiment Viewed" by (event_name, properties_json),
                // LRU-bounded like the JS plugin. Custom events (null key) are never de-duplicated.
                event.dedupeKey?.let { key ->
                    if (dedupeCache.remove(key)) {
                        dedupeCache.add(key)     // refresh recency
                        return@withLock null     // duplicate → skip
                    }
                    dedupeCache.add(key)
                    if (dedupeCache.size > config.resolvedDedupeCacheSize()) {
                        dedupeCache.remove(dedupeCache.iterator().next())  // evict eldest
                    }
                }
                buffer.add(event)
                if (buffer.size >= config.resolvedBatchSize()) {
                    pendingFlush?.cancel()
                    pendingFlush = null
                    buffer.toList().also { buffer.clear() }
                } else {
                    if (pendingFlush == null) {
                        pendingFlush = launch {
                            delay(config.resolvedBatchTimeout().inWholeMilliseconds)
                            scheduledFlush()
                        }
                    }
                    null
                }
            }
            toFlush?.let { flushBatch(it) }
        }
    }

    private suspend fun scheduledFlush() {
        if (closed) return

        val toFlush = mutex.withLock {
            pendingFlush = null
            buffer.toList().also { buffer.clear() }

        }

        if (toFlush.isNotEmpty()) {
            flushBatch(toFlush)
        }
    }

    private fun flushBatch(events: List<TrackingEvent>) {
        if (events.isEmpty() || disabled) return
        val dispatcher = config.networkDispatcher ?: return
        val eventsJson = JsonArray(events.map { it.payload })
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = SdkMetadata.USER_AGENT
        // Match the JS/Python tracking plugins: the JSON array is posted as text/plain.
        // (Accept: application/json is added by the dispatcher.)
        headers["Content-Type"] = "text/plain"
        dispatcher.consumePOSTRequest(
            url = "${config.resolvedIngestorHost()}/track?client_key=${config.clientKey}",
            headers = headers,
            body = eventsJson,
            onSuccess = {},
            onError = { GB.error("Tracking flush failed: ${it.message}", it) }
        )
    }
}
