package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.TrackingNetworkDispatcher
import com.sdk.growthbook.plugin.GBTrackingEventData
import com.sdk.growthbook.plugin.TrackingEvent
import com.sdk.growthbook.plugin.TrackingOptionDefaults
import com.sdk.growthbook.plugin.TrackingOptions
import com.sdk.growthbook.plugin.TrackingPluginConfig
import com.sdk.growthbook.plugin.toTrackingOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.time.Duration

/**
 * Batches experiment/feature evaluation events and POSTs them to the GrowthBook ingest endpoint.
 *
 * A flush is triggered when either the buffer reaches [com.sdk.growthbook.plugin.TrackingPluginConfig.resolvedBatchSize]
 * or a timer fires after [com.sdk.growthbook.plugin.TrackingPluginConfig.resolvedBatchTimeout]. [close] schedules a final
 * flush of any remaining buffered events and then cancels the coroutine scope; [flush] drains the
 * buffer the same way but keeps the plugin running, for the moment the app leaves the foreground.
 *
 * If the client key is null/empty the plugin becomes a no-op: event methods return immediately, no
 * HTTP traffic occurs, and [close] still completes cleanly.
 *
 * Configure it with [Builder]; the [TrackingPluginConfig] constructor is the frozen original entry
 * point and cannot express options added since (see [TrackingPluginConfig]).
 *
 * ```kotlin
 * GrowthBookTrackingPlugin.Builder()
 *     .setClientKey("sdk-abc")
 *     .setNetworkDispatcher(dispatcher)
 *     .setEnableFeatureUsageEvents(false)
 *     .build()
 * ```
 *
 * Besides the automatic feature and experiment events, it implements [CustomEventReceiver], so
 * explicit [com.sdk.growthbook.GrowthBookSDK.logEvent] calls are batched through the same pipeline
 * — matching the JS/Python SDKs' `logEvent` / `log_event`.
 */
class GrowthBookTrackingPlugin internal constructor(
    private val options: TrackingOptions,
    private val coroutineScope: CoroutineScope = CoroutineScope(TrackingDispatcher)
) : GrowthBookPlugin, CustomEventReceiver, AttributesChangeReceiver, FlushablePlugin {

    /**
     * Builds the plugin from the frozen [TrackingPluginConfig]. Still functional, so existing code
     * keeps working, but it cannot express options introduced after the config was frozen — those
     * take their defaults here. Use [Builder] instead.
     */
    @Deprecated("Use GrowthBookTrackingPlugin.Builder()", level = DeprecationLevel.WARNING)
    @Suppress("DEPRECATION")
    constructor(
        config: TrackingPluginConfig,
        coroutineScope: CoroutineScope = CoroutineScope(TrackingDispatcher)
    ) : this(config.toTrackingOptions(), coroutineScope)

    // One switch for every "do nothing at all" reason, checked at the top of each event method so a
    // suppressed event costs nothing beyond the call itself.
    private val disabled = !options.enable || options.clientKey.isNullOrEmpty()
    private val mutex = Mutex()
    private val buffer = mutableListOf<TrackingEvent>()

    /** LRU of recently-seen [TrackingEvent.dedupeKey]s; mirrors the JS plugin's de-dupe cache. */
    private val dedupeCache = LinkedHashSet<String>()
    private var pendingFlush: Job? = null

    @Volatile
    private var closed = false

    override fun init() {
        // A missing client key is a misconfiguration and warns; setEnable(false) is a deliberate
        // kill switch, so it only logs.
        if (!options.enable) {
            GB.log("GrowthBookTrackingPlugin disabled via setEnable(false)")
        } else if (options.clientKey.isNullOrEmpty()) {
            GB.warning("GrowthBookTrackingPlugin disabled: clientKey is null or empty")
        } else if (options.networkDispatcher == null) {
            // Configured to track, but with nothing to track over: flushBatch would drop every
            // batch on the floor. Silence here reads as "tracking works" right up until someone
            // checks the warehouse.
            GB.warning(
                "GrowthBookTrackingPlugin: no networkDispatcher configured — " +
                    "events will be buffered and discarded. Call setNetworkDispatcher()."
            )
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
        val properties = TrackingEvent.experimentProperties(experiment, result)
        if (!passesFilter(TrackingEvent.EVENT_EXPERIMENT_VIEWED, properties, attributes)) {
            return
        }
        enqueue(
            TrackingEvent.from(TrackingEvent.EVENT_EXPERIMENT_VIEWED, properties, attributes),
            attributes,
        )
    }

    override fun onFeatureEvaluated(
        featureKey: String,
        result: GBFeatureResult,
        attributes: Map<String, GBValue>?
    ) {
        // Feature evaluations outnumber experiment exposures by orders of magnitude, so they are
        // what makes an ingest bill or a warehouse quota hurt. Dropped here, before the de-dupe
        // cache and the buffer, so a suppressed event costs nothing at all. Experiment views and
        // their bandit attribution are untouched — mirrors the TS plugin's enableFeatureUsageEvents.
        if (disabled || closed || !options.enableFeatureUsageEvents) {
            return
        }
        val properties = TrackingEvent.featureProperties(featureKey, result)
        if (!passesFilter(TrackingEvent.EVENT_FEATURE_EVALUATED, properties, attributes)) {
            return
        }
        enqueue(
            TrackingEvent.from(TrackingEvent.EVENT_FEATURE_EVALUATED, properties, attributes),
            attributes,
        )
    }

    /**
     * Batches an explicit [com.sdk.growthbook.GrowthBookSDK.logEvent] call alongside the
     * auto-tracked events. Not gated by `enableFeatureUsageEvents`, and not de-duplicated unless it
     * is named after one of the SDK's own events — the rule follows the event name (see
     * [com.sdk.growthbook.plugin.TrackingEvent.from]), as in the JS plugin. Both concern the SDK's
     * own high-volume events, while a custom event is sent because the caller asked for it.
     */
    override fun onEvent(
        eventName: String,
        properties: Map<String, GBValue>,
        attributes: Map<String, GBValue>?
    ) {
        if (closed || disabled) {
            return
        }
        if (!passesFilter(eventName, properties, attributes)) {
            return
        }
        enqueue(
            TrackingEvent.from(eventName, properties, attributes),
            attributes,
        )
    }

    /**
     * Drops the de-duplication history when the instance starts serving a different user.
     *
     * A `Feature Evaluated` event carries no unit identity — its properties are
     * feature/source/value/ruleId/variationId — so without this the next user's identical
     * evaluation is suppressed as a duplicate of the previous user's and never reaches the
     * warehouse. One SDK instance serving several users in turn is the norm on a client
     * (login, logout, account switching), which is exactly why this is not left to configuration.
     *
     * [Builder.setDedupeKeyAttributes] remains useful and is unaffected: it separates users
     * *within* the cache, which also covers events already buffered for the previous user. The
     * clear is cheap and unconditional — an extra event costs a row, a dropped one costs an
     * exposure.
     *
     * A clear is all this layer can do, and it is weaker than the SDK's own gate. The usage history
     * on [com.sdk.growthbook.model.EvalSnapshot] is *swapped* per user, so an evaluation that began
     * before the change writes into the previous user's history; this cache has no notion of whose
     * event it holds, so the same late event does take a slot here and suppresses the next user's
     * identical one. [Builder.setDedupeKeyAttributes] is the fix for that window — the late event
     * carries the previous user's attributes and keys elsewhere.
     *
     * `Experiment Viewed` would survive without this (it carries hashAttribute/hashValue), but the
     * cache is a single LRU, so it goes with the rest.
     */
    override fun onAttributesChanged(attributes: Map<String, GBValue>) {
        if (closed || disabled) {
            return
        }
        // Takes the same lock as enqueue rather than clearing in place: the cache is only ever
        // touched from the tracking scope under the mutex.
        coroutineScope.launch {
            mutex.withLock { dedupeCache.clear() }
        }
    }

    /**
     * Runs the consumer's event filter, if one is registered.
     *
     * Called from the event methods rather than from [enqueue] for two reasons: [enqueue] runs
     * under [mutex] on the tracking scope, and a consumer predicate has no business holding the
     * batching lock; and the reference JS plugin filters *before* de-duplication, so a dropped
     * event must not occupy a slot in the LRU cache.
     *
     * A throwing filter drops the event. Privacy is the main reason this hook exists, and sending
     * is the only outcome that can leak — when the filter's intent is unknowable, not sending is
     * the safe answer.
     */
    private fun passesFilter(
        eventName: String,
        properties: Map<String, GBValue>,
        attributes: Map<String, GBValue>?
    ): Boolean {
        val filter = options.eventFilter ?: return true
        return try {
            filter(GBTrackingEventData(eventName, properties, attributes))
        } catch (t: Throwable) {
            GB.warning("Tracking eventFilter threw for '$eventName'; dropping the event: $t")
            false
        }
    }

    /**
     * Sends whatever is buffered now and keeps the plugin running — [close]'s body without the
     * shutdown. Safe to call repeatedly, with an empty buffer, and concurrently with evaluation.
     *
     * Call it when the app leaves the foreground (`ON_STOP` on Android,
     * `applicationDidEnterBackground` / `scenePhase` on Apple). That is the window the batch timer
     * cannot cover: a process killed in the background takes its buffer with it, so events sit
     * there for up to the batch timeout and then vanish — silent loss, not a delay. The reference
     * JS plugin flushes on `visibilitychange`/`pagehide`; there is no portable equivalent here,
     * because the SDK deliberately takes no dependency on any platform's lifecycle (the same
     * reason `startPolling`/`stopPolling` are the consumer's to call), so the SDK exposes the drain
     * and the host app picks the moment.
     *
     * Suspends until the batch has been handed to
     * [com.sdk.growthbook.network.TrackingNetworkDispatcher], **not** until it has been delivered —
     * no dispatcher reports delivery. Waiting for the hand-off is the whole point: a fire-and-forget
     * drain returns before the buffer has even been read, which against a process about to be killed
     * is worth nothing. Call it on the way into the background rather than as the process dies.
     *
     * Suspend rather than a pair of blocking/awaiting variants: the operation has a completion worth
     * waiting for, and a caller who does not want to wait says so explicitly with
     * `scope.launch { plugin.flush() }` — which also makes it their scope rather than the plugin's.
     * Android's `ON_STOP` is not a suspend context, so go through `lifecycleScope` there (see the
     * README); on Apple the Objective-C export is an `async` function, usable straight from a `Task`
     * in `applicationDidEnterBackground` / a `scenePhase` observer.
     *
     * The drain itself still runs on the plugin's scope rather than the caller's thread: that scope
     * is effectively single-worker, which is what keeps a flush from overtaking an event still being
     * enqueued (see [Builder.setCoroutineScope]).
     *
     * Callable directly, as here, or through [com.sdk.growthbook.GrowthBookSDK.flush], which drains
     * every registered [FlushablePlugin] at once — use that when the plugin went in through
     * `setPlugins(...)` and no reference to it was kept.
     *
     * The pending timer is cancelled *and* cleared rather than merely cancelled: [enqueue] only
     * starts a new one while `pendingFlush` is null, so leaving a dead job there would strand every
     * later event in the buffer until the next size-triggered flush.
     */
    override suspend fun flush() {
        if (closed || disabled) return
        coroutineScope.launch {
            val toFlush = mutex.withLock {
                pendingFlush?.cancel()
                pendingFlush = null
                buffer.toList().also { buffer.clear() }
            }
            if (toFlush.isNotEmpty()) flushBatch(toFlush)
        }.join()
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

    private fun enqueue(event: TrackingEvent, attributes: Map<String, GBValue>?) {
        coroutineScope.launch {
            val toFlush = mutex.withLock {
                // De-dupe "Feature Evaluated"/"Experiment Viewed" by (event_name, properties_json)
                // plus any configured identity attributes, LRU-bounded like the JS plugin. Custom
                // events (null key) are never de-duplicated.
                event.dedupeKey?.let { baseKey ->
                    val key = baseKey + dedupeAttributeSuffix(attributes)
                    if (dedupeCache.remove(key)) {
                        dedupeCache.add(key)     // refresh recency
                        return@withLock null     // duplicate → skip
                    }
                    dedupeCache.add(key)
                    if (dedupeCache.size > options.dedupeCacheSize) {
                        dedupeCache.remove(dedupeCache.iterator().next())  // evict eldest
                    }
                }
                buffer.add(event)
                if (buffer.size >= options.batchSize) {
                    pendingFlush?.cancel()
                    pendingFlush = null
                    buffer.toList().also { buffer.clear() }
                } else {
                    if (pendingFlush == null) {
                        pendingFlush = launch {
                            delay(options.batchTimeout.inWholeMilliseconds)
                            scheduledFlush()
                        }
                    }
                    null
                }
            }
            toFlush?.let { flushBatch(it) }
        }
    }

    /**
     * Identity segment appended to [TrackingEvent.dedupeKey]. Empty unless
     * [GrowthBookTrackingPlugin.Builder.setDedupeKeyAttributes] was configured, so the default
     * behavior is unchanged.
     *
     * Why it is needed: a "Feature Evaluated" key carries no unit identity — its properties are
     * feature/source/value/ruleId/variationId — so one reused SDK instance whose user changed
     * (`setAttributes` on login/logout) would have the new user's event dropped as a duplicate of
     * the previous user's. "Experiment Viewed" already carries hashAttribute/hashValue in its
     * properties and does not have the problem, but the configured attributes are applied to both,
     * matching the TS plugin.
     *
     * A listed attribute that is absent is omitted rather than written as null, mirroring
     * `JSON.stringify` dropping undefined fields in the TS implementation — otherwise two callers
     * that TS treats as identical would produce different keys here.
     */
    private fun dedupeAttributeSuffix(attributes: Map<String, GBValue>?): String {
        if (options.dedupeKeyAttributes.isEmpty()) return ""
        return buildJsonObject {
            options.dedupeKeyAttributes.forEach { key ->
                attributes?.get(key)?.let { put("attr:$key", it.gbSerialize()) }
            }
        }.toString()
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
        val dispatcher = options.networkDispatcher ?: return
        val eventsJson = JsonArray(events.map { it.payload })
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = SdkMetadata.USER_AGENT
        // Match the JS/Python tracking plugins: the JSON array is posted as text/plain.
        // (Accept: application/json is added by the dispatcher.)
        headers["Content-Type"] = "text/plain"
        dispatcher.consumePOSTRequest(
            url = "${options.ingestorHost}/track?client_key=${options.clientKey}",
            headers = headers,
            body = eventsJson,
            onSuccess = {},
            onError = { GB.error("Tracking flush failed: ${it.message}", it) }
        )
    }

    /**
     * Fluent builder — the supported way to configure the plugin.
     *
     * Every option lives here, and every future one arrives as another setter. Adding a setter
     * neither breaks source nor binary compatibility, so the configuration surface can keep growing
     * within a minor release: the values it collects land in the internal
     * [com.sdk.growthbook.plugin.TrackingOptions], never in a public constructor signature. Unset
     * options fall back to the same defaults the frozen [TrackingPluginConfig] applies.
     */
    class Builder {
        private var ingestorHost: String? = null
        private var clientKey: String? = null
        private var batchSize: Int? = null
        private var batchTimeout: Duration? = null
        private var networkDispatcher: TrackingNetworkDispatcher? = null
        private var dedupeCacheSize: Int? = null
        private var enableFeatureUsageEvents: Boolean = true
        private var coroutineScope: CoroutineScope? = null
        private var dedupeKeyAttributes: List<String> = emptyList()
        private var enable: Boolean = true
        private var eventFilter: ((GBTrackingEventData) -> Boolean)? = null

        /** Base URL of the ingest endpoint. Events are POSTed to `{ingestorHost}/track`. */
        fun setIngestorHost(ingestorHost: String?): Builder {
            this.ingestorHost = ingestorHost
            return this
        }

        /**
         * Client key (SDK connection key). If null/empty the plugin becomes a no-op — it will not
         * make HTTP requests but [close] still completes cleanly.
         */
        fun setClientKey(clientKey: String?): Builder {
            this.clientKey = clientKey
            return this
        }

        /** Max events buffered before an eager flush. */
        fun setBatchSize(batchSize: Int?): Builder {
            this.batchSize = batchSize
            return this
        }

        /** Max time an event sits in the buffer before a scheduled flush. */
        fun setBatchTimeout(batchTimeout: Duration?): Builder {
            this.batchTimeout = batchTimeout
            return this
        }

        /** Network dispatcher used to POST tracking events. */
        fun setNetworkDispatcher(networkDispatcher: TrackingNetworkDispatcher?): Builder {
            this.networkDispatcher = networkDispatcher
            return this
        }

        /**
         * Max number of recently-seen "Feature Evaluated"/"Experiment Viewed" events kept for
         * de-duplication. Repeated events with identical properties within this LRU window are
         * dropped before enqueueing (mirrors the JS tracking plugin).
         */
        fun setDedupeCacheSize(dedupeCacheSize: Int?): Builder {
            this.dedupeCacheSize = dedupeCacheSize
            return this
        }

        /**
         * Master switch for the whole plugin. Default true; set false and every event method
         * returns immediately — nothing is buffered, de-duplicated or sent, and [close] still
         * completes cleanly.
         *
         * Use it to keep the plugin registered while silencing it (a debug build, an environment
         * without an ingest quota, a consent gate that has not been granted) instead of building
         * two different plugin lists. For dropping only the high-volume half, see
         * [setEnableFeatureUsageEvents]. Mirrors the TS plugin's `enable`.
         *
         * Note this is the plugin's switch, not the SDK's: [com.sdk.growthbook.GBSDKBuilder.setEnabled]
         * turns feature evaluation itself on and off.
         */
        fun setEnable(enable: Boolean): Builder {
            this.enable = enable
            return this
        }

        /**
         * Whether "Feature Evaluated" events are sent to the ingest endpoint. Default true.
         *
         * Set false to keep experiment exposures (including their bandit attribution) and drop
         * feature-usage events, which are by far the higher-volume of the two and the ones that
         * consume an ingest or warehouse quota. Mirrors the TS plugin's `enableFeatureUsageEvents`.
         * It does not affect [com.sdk.growthbook.GBSDKBuilder.setFeatureUsageCallback], which
         * reports evaluations locally and never leaves the process.
         */
        fun setEnableFeatureUsageEvents(enabled: Boolean): Builder {
            this.enableFeatureUsageEvents = enabled
            return this
        }

        /**
         * Scope on which batching and flushing run. Defaults to the platform's tracking dispatcher.
         *
         * Two constraints come with overriding it, both easy to violate by accident:
         *
         * - **The plugin takes ownership.** [close] cancels this scope, so handing it a shared one
         *   (`viewModelScope`, an application scope) tears down whatever else was running there
         *   when the SDK closes. A scope built without a `Job` — `object : CoroutineScope { override
         *   val coroutineContext = Dispatchers.IO }` — is worse: cancelling it throws. Give the
         *   plugin a scope of its own.
         * - **It must be effectively single-worker.** The default is
         *   `PlatformDependentIODispatcher.limitedParallelism(1)` so buffered events keep their
         *   submission order and a final flush cannot overtake an event still being enqueued. With
         *   a multi-worker scope an event enqueued while [close] drains the buffer lands after the
         *   final flush and is discarded with the scope, and the de-dupe cache clear scheduled by
         *   [onAttributesChanged] can run *after* the new user's first evaluation, dropping the very
         *   event it exists to preserve. Mutual exclusion itself does not depend on this — the
         *   buffer, the de-dupe cache and the pending-flush handle are all behind a `Mutex` — only
         *   ordering does.
         *
         * Mainly a test seam: pass a scope backed by a test dispatcher to drive batching
         * deterministically.
         */
        fun setCoroutineScope(coroutineScope: CoroutineScope?): Builder {
            this.coroutineScope = coroutineScope
            return this
        }

        /**
         * Attributes folded into the de-duplication key, on top of the event name and its
         * properties. Empty by default, which keeps the plugin's original behaviour.
         *
         * Set this when one SDK instance serves more than one user over its lifetime (login,
         * logout, account switching via `setAttributes`). A "Feature Evaluated" event has no unit
         * identity of its own, so without it the next user's identical evaluation is suppressed as
         * a duplicate of the previous user's. Listing the identifier attribute — usually `"id"` —
         * keeps the two apart. Mirrors the TS plugin's `dedupeKeyAttributes`.
         */
        fun setDedupeKeyAttributes(keys: List<String>): Builder {
            // Copied: the caller may hold a mutable list, and mutating it afterwards would reshape
            // the de-duplication key at runtime.
            this.dedupeKeyAttributes = keys.toList()
            return this
        }

        /**
         * Predicate deciding whether an event is sent at all. Receives the event before it is
         * serialized or de-duplicated; returning false drops it silently. No filter by default.
         *
         * The main use is privacy — dropping events whose attributes must not leave the device —
         * but it also serves consent gates (unlike [setEnable], it is consulted per event, so it
         * can read state that changes at runtime), cost control by event name, and sampling.
         *
         * It runs on the evaluation path, so keep it cheap and non-blocking. It cannot redact
         * individual fields: the decision is all-or-nothing for the whole event. A filter that
         * throws drops the event, since sending is the only outcome that can leak.
         *
         * Mirrors the TS plugin's `eventFilter`.
         *
         * ```kotlin
         * .setEventFilter { event -> event.attributes?.get("internalUser") != GBBoolean(true) }
         * ```
         */
        fun setEventFilter(eventFilter: ((GBTrackingEventData) -> Boolean)?): Builder {
            this.eventFilter = eventFilter
            return this
        }

        fun build(): GrowthBookTrackingPlugin = GrowthBookTrackingPlugin(
            options = TrackingOptions(
                ingestorHost = TrackingOptionDefaults.ingestorHost(ingestorHost),
                clientKey = clientKey,
                batchSize = TrackingOptionDefaults.batchSize(batchSize),
                batchTimeout = TrackingOptionDefaults.batchTimeout(batchTimeout),
                networkDispatcher = networkDispatcher,
                dedupeCacheSize = TrackingOptionDefaults.dedupeCacheSize(dedupeCacheSize),
                enableFeatureUsageEvents = enableFeatureUsageEvents,
                dedupeKeyAttributes = dedupeKeyAttributes,
                enable = enable,
                eventFilter = eventFilter,
            ),
            coroutineScope = coroutineScope ?: CoroutineScope(TrackingDispatcher),
        )
    }
}
