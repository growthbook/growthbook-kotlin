@file:OptIn(ExperimentalAtomicApi::class)

package com.sdk.growthbook.features

import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

// Upper bound on a single network round in [FeaturesViewModel.runNetworkRound]. Both recommended
// dispatchers (Ktor/OkHttp) default to an INFINITE read timeout so a server that accepts the
// connection then never responds would otherwise leave awaitRefresh()/suspendFeature() hanging
// forever. Kept dispatcher-agnostic here so custom NetworkDispatcher impls are bounded too.
private const val NETWORK_ROUND_TIMEOUT_MILLIS = 30_000L

/**
 * Interface for Feature API Completion Events
 */
internal interface FeaturesFlowDelegate {
    fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean)
    suspend fun onPayloadReady(model: FeaturesDataModel)
    fun featuresFetchFailed(error: GBError, isRemote: Boolean)
    fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean)
    fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean)
    fun featuresNotModified()
}

/**
 * Orchestrates feature loading: serves cache first, then falls back to the
 * network, decodes the payload and notifies [delegate] of the outcome.
 * Pipeline: source (cache|network|SSE) -> decode -> FetchOutcome -> dispatch.
 */
internal class FeaturesViewModel(
    /**
     * Receiver of fetch outcomes. Every cache/network/SSE result is reported here
     * (success, failure, 304-not-modified, saved groups) so the owning SDK can
     * apply the features to its context and fire the refresh handler.
     */
    private val delegate: FeaturesFlowDelegate,
    /**
     * Performs the actual fetches, independent of caching: network GET (with
     * 304 handling), remote-eval POST, and the SSE stream. This view model adds
     * the cache and coalescing logic on top of it.
     */
    private val dataSource: FeaturesDataSource,
    /**
     * Key used to decrypt encrypted payloads; null when features arrive in
     * plaintext. Also seeds the default [decoder].
     */
    private val encryptionKey: String? = null,
    /**
     * Gates cache *writes* only: when true, a successful network payload is
     * persisted to [cachingLayer]. Cache *reads* (see [serveCache]) happen
     * regardless, so bundled/previously cached data can still be served.
     */
    private val cachingEnabled: Boolean,
    /**
     * Filename under which the payload is stored in [cachingLayer]. The caller
     * namespaces it per API key to keep multiple SDK instances isolated.
     */
    private val cacheKey: String = Constants.FEATURE_CACHE,
    /**
     * Storage backend for the cached payload: read in [serveCache], written in
     * [handleNetworkModel]. Defaults to the platform's shared caching layer.
     */
    private val cachingLayer: CachingLayer = CachingImpl.getLayer(),
    /**
     * Cache freshness window in milliseconds. While the cached payload is younger
     * than this, a [FetchPolicy.CacheFirst] fetch is served from cache and the
     * network is skipped; once older, the SDK refetches. null means always refetch.
     * Mirrors [com.sdk.growthbook.GBSDKBuilder.setCacheMaxAge].
     */
    private val cacheMaxAge: Long? = null,
    /**
     * Inner "fresh" window in milliseconds that arms the three-tier stale-while-revalidate model in
     * [serveCache] together with [cacheMaxAge]:
     *   - age < staleTtl                -> fresh: served from cache, network skipped
     *   - staleTtl <= age < cacheMaxAge -> stale: served immediately + revalidated over the network
     *   - age >= cacheMaxAge            -> expired: NOT served, refetched (cache miss)
     * When null the outer cutoff is disarmed and [cacheMaxAge] alone governs the skip-network window
     * (single-threshold 7.3.0 behaviour, unchanged for existing consumers). Mirrors
     * [com.sdk.growthbook.GBSDKBuilder.setStaleTtl].
     */
    private val staleTtl: Long? = null,
    /**
     * HTTP `stale-if-error` toggle for the expired (third) tier. When true, a cache older than
     * [cacheMaxAge] (with [staleTtl] set) is served as a last resort — but only after the
     * revalidating network round fails, so evaluation never sees data past its freshness ceiling
     * while the network is reachable. Default false fails closed. Handled inside [cachePolicy].
     * Mirrors [com.sdk.growthbook.GBSDKBuilder.setServeStaleOnError].
     */
    private val serveStaleOnError: Boolean = false,
    /**
     * Turns a raw [FeaturesDataModel] into a [DecodedPayload] (features + saved
     * groups), decrypting with [encryptionKey] when needed. Injectable for tests.
     */
    private val decoder: FeaturePayloadDecoder = FeaturePayloadDecoder(encryptionKey),
    /**
     * Whether this view model fetches via remote evaluation (POST) instead of a plain GET.
     * When true, the coalesced refresh ([awaitRefresh], used by suspendFeature() retries) issues
     * a remote-eval POST built from [remoteEvalPayloadProvider] rather than a bare GET, so a retry
     * cannot momentarily surface non-personalized (unevaluated) features. Mirrors the reference
     * sdk-js `fetchFeatures()` branching on `isRemoteEval()`.
     */
    private val remoteEval: Boolean = false,
    /**
     * Builds the remote-eval request payload (attributes + forced features + forced variations)
     * live at call time. Invoked only when [remoteEval] is true; kept live so a retry reflects the
     * latest context. The payload lives on the owning SDK, hence the provider seam.
     */
    private val remoteEvalPayloadProvider: () -> GBRemoteEvalParams? = { null },
    /**
     * Context on which fetched payloads are processed (decode + sticky-bucket
     * refresh + feature application) and on which the coalesced refresh started by
     * [awaitRefresh] (used by suspendFeature() retries and [revalidate]) runs. It
     * must outlive any single caller, so cancelling one caller never tears down a
     * refresh shared with others; it is wrapped in a [SupervisorJob] so one failed
     * refresh never tears down the shared scope. Injectable so tests can supply a
     * deterministic dispatcher.
     */
    coroutineContext: CoroutineContext
) {

    /**
     * SSEConnectionController for managing the lifecycle state
     */
    val sseController: SSEConnectionController
        get() = dataSource.sseController

    // Last-resort guard for fire-and-forget children (the payload-processing launch in
    // performNetworkRound / fetchFromNetwork): an exception escaping one of them — e.g. a consumer
    // refreshHandler that throws while a payload is applied — must not reach the platform default
    // handler, which on Android crashes the app. It is logged here instead; the failed round is
    // already reported to the delegate. (The SupervisorJob keeps one failed child from tearing down
    // the shared scope; the handler keeps its exception from propagating past the scope.)
    private val uncaughtHandler = CoroutineExceptionHandler { _, throwable ->
        GB.error("FeaturesViewModel: uncaught error in background scope", throwable)
    }

    /**
     * Long-lived scope for all background work: payload processing, the coalesced refresh
     * ([awaitRefresh]) and the poll loop ([poller]). Built from the injected [coroutineContext] plus
     * a [SupervisorJob] (one failed child never tears down the others or the scope) and
     * [uncaughtHandler] (an exception escaping a child is logged, not crashed on). Cancelled in
     * [close], so background work never outlives the owning SDK.
     */
    private val coroutineScope: CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob() + uncaughtHandler)

    /**
     * Guards reads and writes of [inFlight] so the check-or-start decision in
     * [awaitRefresh] is atomic across concurrent callers. Held only for that brief
     * bookkeeping, never for the duration of the network call.
     */
    private val refreshMutex = Mutex()

    /**
     * The currently running coalesced refresh, or null when none is in flight.
     * Concurrent [awaitRefresh] callers await this same [Deferred] instead of each
     * starting their own request; it is cleared once the request completes (in a
     * `finally`, before the result is observable), so the next round starts fresh.
     */
    private var inFlight: Deferred<FetchResult>? = null

    /**
     * Monotonic counter bumped when a remote-eval round is initiated, by the wrappers that own the
     * ordering point: [fetchFromNetwork] increments synchronously *before* its `launch` (so the
     * generation tracks the payload captured at the same call), and [runNetworkRound] increments
     * in-coroutine right before [performNetworkRound]. A remote-eval response is applied/reported
     * only while its captured generation is still the latest, so a slow older POST cannot overwrite
     * the result of a newer one (out-of-order responses after rapid attribute/forced-feature changes).
     * The generation check and the (suspend) apply are made atomic by [applyMutex]; the check alone is
     * not enough — see there.
     */
    private val remoteEvalGeneration = AtomicLong(0)

    /**
     * Serializes the *application* of a remote-eval payload (the generation check + [handleNetworkModel]).
     * Without it the `generation == remoteEvalGeneration.load()` check and the suspend apply are not
     * atomic: on a multi-threaded dispatcher an older round whose check passed could finish its slow
     * sticky-bucket apply *after* a newer round already applied, overwriting the newer personalized
     * features. Holding this lock across check+apply makes the newest generation win deterministically.
     */
    private val applyMutex = Mutex()

    // Background polling loop + its backoff/CAS lifecycle live in Poller, not this view model. Jitter
    // spreads the post-failure backoff so many instances that failed together (e.g. after a server
    // outage) do not all re-poll in lockstep.
    private val poller = Poller(coroutineScope, jitterFactor = 0.5)

    // All cache-freshness decisions (zone classification + stale-if-error) live in one place; the
    // require on staleTtl < cacheMaxAge fires from CachePolicy's init during construction.
    private val cachePolicy = CachePolicy(staleTtl, cacheMaxAge, serveStaleOnError)

    /**
     * The single active auto-refresh mechanism. SSE and polling are mutually exclusive; this atomic
     * is the arbiter that makes the transition race-free even when [autoRefreshFeatures] and
     * [startPolling] are called concurrently from different threads (see those methods).
     */
    private enum class RefreshMode { NONE, POLLING, SSE }

    private val refreshMode = AtomicReference(RefreshMode.NONE)

    /** Opens an SSE connection and streams feature updates as a [Flow]. */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        // SSE supersedes polling and always wins a concurrent race: claim the mode FIRST, then tear
        // down any poller. Ordering matters — a startPolling() racing this either observes SSE already
        // claimed and bails, or its post-start re-check (below) sees SSE and stops the poller it just
        // started, so the two can never end up both active.
        refreshMode.store(RefreshMode.SSE)
        poller.stop()
        sseController.start()
        return dataSource.autoRefreshRaw().transform { resource ->
            when (resource) {
                is Resource.Error -> {
                    emit(resource)
                    dispatch(FetchOutcome.Failed(GBError(resource.exception), source = Source.NETWORK))
                }
                is Resource.Success -> {
                    // Process the payload on coroutineScope (platform IO) like the network GET /
                    // remote-eval paths, so decode + sticky-bucket refresh + feature application
                    // never run on the collector's thread (e.g. Dispatchers.Main) and are torn down
                    // by close(). await() resumes on the collector coroutine, so emit stays
                    // flow-safe, and we still emit the same FetchOutcome the pipeline dispatched.
                    val outcome = coroutineScope.async { handleNetworkModel(resource.data) }.await()
                    when (outcome) {
                        is FetchOutcome.Ready -> emit(Resource.Success(outcome.payload.features))
                        else -> emit(Resource.Error(Exception("Failed to decode features payload")))
                    }
                }
            }
        }
    }

    /**
     * Starts background polling every [intervalMs]. No-op (returns false) when SSE is active — the
     * two are mutually exclusive — or when a poll loop is already running. Each round goes through the
     * coalesced [awaitRefresh], so polling honours the same remote-eval/GET mode as everything else.
     */
    fun startPolling(intervalMs: Long): Boolean {
        // Never poll under SSE. Claim POLLING from NONE (a no-op CAS when we already hold it).
        if (refreshMode.load() == RefreshMode.SSE) return false
        refreshMode.compareAndSet(RefreshMode.NONE, RefreshMode.POLLING)
        val started = poller.start(intervalMs) { awaitRefresh() }
        // Close the race window: if autoRefreshFeatures() switched us to SSE while we were starting,
        // undo the poller we just launched so SSE stays the sole active mechanism.
        if (refreshMode.load() == RefreshMode.SSE) {
            poller.stop()
            return false
        }
        return started
    }

    /** Stops the background poll loop and releases the polling mode. Safe to call when not polling. */
    fun stopPolling() {
        poller.stop()
        refreshMode.compareAndSet(RefreshMode.POLLING, RefreshMode.NONE)
    }

    /** Stops the SSE connection and releases the auto-refresh mode so polling can be started again. */
    fun stopAutoRefresh() {
        sseController.stop()
        refreshMode.compareAndSet(RefreshMode.SSE, RefreshMode.NONE)
    }

    /**
     * Whether this fetches remotely (POST) or via a regular GET is decided by the instance
     * [remoteEval] mode, not per call.
     *
     * @param payload attributes/forced features sent with the remote-eval request;
     *   used only in [remoteEval] mode, ignored otherwise.
     * @param policy whether a fresh cache may satisfy this fetch or the network
     *   must always be hit; see [FetchPolicy].
     */
    fun fetchFeatures(
        payload: GBRemoteEvalParams? = null,
        policy: FetchPolicy = FetchPolicy.CacheFirst
    ) {
        when (val result = serveCache(policy)) {
            is CacheOutcome.Expired -> fetchFromNetwork(payload = payload, onErrorFallback = result.stale)
            CacheOutcome.ServedFresh -> return
            CacheOutcome.ServedStaleOrMiss -> fetchFromNetwork(payload = payload, onErrorFallback = null)
        }
    }

    /**
     * Coalesced network refresh: concurrent callers share a single in-flight
     * GET instead of each firing their own. The first caller starts the request
     * and stores its [Deferred] in [inFlight]; callers arriving while it runs
     * await the same result. The slot is cleared once the request completes, so
     * the next round (e.g. a backoff retry) starts a fresh request.
     *
     * This is the network primitive beneath the cache-aware [fetchFeatures]; it always hits the
     * network (a remote-eval POST when [remoteEval] is set, otherwise a GET) and carries no
     * [FetchPolicy].
     */
    suspend fun awaitRefresh(): FetchResult {
        val deferred = refreshMutex.withLock {
            inFlight ?: coroutineScope.async {
                try {
                    runNetworkRound()
                } finally {
                    refreshMutex.withLock { inFlight = null }
                }
            }.also { inFlight = it }
        }
        return deferred.await()
    }

    /**
     * Stale-while-revalidate refresh backing [com.sdk.growthbook.GrowthBookSDK.refreshCache].
     * Serves any cached payload immediately as non-authoritative, then triggers a
     * coalesced network revalidation that joins an in-flight [awaitRefresh] instead
     * of starting a duplicate request. Always bypasses cache freshness (see
     * [FetchPolicy.ForceNetwork]).
     *
     * Fire-and-forget: the network round runs on [coroutineScope]; observe completion
     * via the refresh handler rather than expecting features to be ready on return.
     */
    fun revalidate() {
        serveCache(policy = FetchPolicy.ForceNetwork)
        coroutineScope.launch { awaitRefresh() }
    }

    /**
     * Releases resources held by this view model: stops any active SSE connection and cancels the
     * coroutine scope used to process fetched payloads, so in-flight background work does not outlive
     * the owning SDK instance. Safe to call more than once.
     */
    fun close() {
        stopPolling()
        stopAutoRefresh()
        coroutineScope.cancel()
    }

    /**
     * The single network round: issues a remote-eval POST when [remoteEval] is true (mirroring the
     * reference sdk-js `fetchFeatures()` branch on `isRemoteEval()`), otherwise a plain GET with 304
     * handling, and feeds every result through the same [dispatch] pipeline. Returns only after the
     * payload is fully applied (incl. the suspend sticky-bucket refresh in [handleNetworkModel] /
     * onPayloadReady), so callers observe a completed refresh, not just a completed HTTP round.
     *
     * This is the one place the data-source callbacks are wired; [fetchFromNetwork] (fire-and-forget,
     * cache-aware) and [runNetworkRound] (coalesced, timeout-bounded) are thin wrappers over it.
     */
    private suspend fun performNetworkRound(
        payload: GBRemoteEvalParams?,
        generation: Long,
        onErrorFallback: DecodedPayload? = null
    ): FetchResult = suspendCancellableCoroutine { cont ->
        // Resume the continuation at most once: the synchronous catch below and an async data-source
        // callback could otherwise both resume it (e.g. a misbehaving dispatcher that invokes a
        // callback and then also throws synchronously), which is a hard error for a continuation.
        fun resumeOnce(result: FetchResult) {
            if (cont.isActive) cont.resume(result)
        }
        try {
            if (remoteEval) {
                // [generation] tags this remote-eval round (assigned by the caller at the ordering
                // point). Remote-eval POSTs are independent and un-cancelled, so a slower older one
                // can complete after a newer one. Apply/report a response only while it is still the
                // latest generation, otherwise a stale evaluation (e.g. for the previous attributes)
                // would overwrite the current one. A superseded round still resumes its continuation
                // (as FetchResult.Superseded, never Success) so the awaiter re-joins the current
                // generation instead of hanging or being told a discarded round succeeded.
                dataSource.fetchRemoteEval(
                    params = payload,
                    success = {
                        coroutineScope.launch {
                            // Check the generation and apply the payload atomically under applyMutex:
                            // the check must hold until handleNetworkModel finishes, or a newer round
                            // could apply in between and then be overwritten by this (older) one.
                            //
                            // This up-front check is necessary but NOT sufficient: handleNetworkModel
                            // suspends (onPayloadReady's sticky-bucket refresh) and the generation is
                            // bumped outside applyMutex (incrementAndFetch in runNetworkRound /
                            // fetchFromNetwork), so a newer round can supersede this one *during* that
                            // suspend. handleNetworkModel therefore re-validates the generation at the
                            // final commit point and returns false when it declined to commit — mapped
                            // to Superseded here so a stale round never overwrites nor reports Success.
                            applyMutex.withLock {
                                if (generation == remoteEvalGeneration.load()) {
                                    // persistToCache = false: the remote-eval cache is keyed only by API
                                    // key (FeatureCache_<apiKey>), so persisting this user's evaluated
                                    // payload would let it leak to the next user on the same key. Mirror
                                    // the read-side bypass in serveCache — remote-eval never touches the cache.
                                    val outcome = handleNetworkModel(it.data, generation, persistToCache = false)
                                    resumeOnce(
                                        if (outcome != null) FetchResult.Success else FetchResult.Superseded
                                    )
                                } else {
                                    // Stale: payload not applied → do not report Success. Always resume
                                    // (no leak / no 30s timeout hang), but with a distinct result.
                                    resumeOnce(FetchResult.Superseded)
                                }
                            }
                        }
                    },
                    failure = {
                        if (generation == remoteEvalGeneration.load()) {
                            dispatch(FetchOutcome.Failed(GBError(it.exception), source = Source.NETWORK))
                            resumeOnce(FetchResult.Failed)
                        } else {
                            // A stale failure is not the current fetch's failure either.
                            resumeOnce(FetchResult.Superseded)
                        }
                    }
                )
            } else {
                dataSource.fetchFeatures(
                    success = {
                        coroutineScope.launch {
                            handleNetworkModel(it)
                            resumeOnce(FetchResult.Success)
                        }
                    },
                    failure = {
                        if (cachePolicy.serveStaleOnError && onErrorFallback != null) {
                            // stale-if-error: serve the expired cache as a NON-authoritative payload.
                            // Intentionally silent to the refresh handler — a non-authoritative Ready
                            // fires neither the success nor the failure delegate path. The handler's
                            // (Boolean, GBError?) contract cannot express "stale fallback served", so
                            // signalling either side would mislead. Documented on
                            // GBSDKBuilder.setServeStaleOnError; revisit if the handler ever grows a
                            // dedicated stale signal.
                            dispatch(FetchOutcome.Ready(onErrorFallback, source = Source.CACHE, authoritative = false))
                        } else {
                            dispatch(FetchOutcome.Failed(GBError(it), source = Source.NETWORK))
                        }
                        resumeOnce(FetchResult.Failed)
                    },
                    onNotModified = {
                        dispatch(FetchOutcome.NotModified)
                        resumeOnce(FetchResult.NotModified)
                    }
                )
            }
        } catch (cancellation: CancellationException) {
            // Never swallow cancellation: it must propagate so runNetworkRound's withTimeoutOrNull and
            // close()/coroutineScope cancellation keep working. Unlike a real error it is not reported
            // as a fetch failure.
            throw cancellation
        } catch (t: Throwable) {
            // A dispatcher that throws synchronously while enqueuing the request (e.g. a custom
            // NetworkDispatcher) must be surfaced as a normal fetch failure — not swallowed by the
            // coroutine machinery (fire-and-forget path) nor rethrown into initialize()/setAttributes()
            // (coalesced path). Both wrappers then behave consistently.
            dispatch(FetchOutcome.Failed(GBError(t), source = Source.NETWORK))
            resumeOnce(FetchResult.Failed)
        }
    }

    /**
     * The coalesced-refresh runner behind [awaitRefresh]: wraps [performNetworkRound] with a bounded
     * timeout so a dispatcher that accepts the request but never calls back cannot hang
     * suspendFeature()'s retry loop forever. Uses the instance [remoteEval] mode with a live payload
     * from [remoteEvalPayloadProvider].
     */
    private suspend fun runNetworkRound(): FetchResult =
        withTimeoutOrNull(NETWORK_ROUND_TIMEOUT_MILLIS.milliseconds) {
            val generation = if (remoteEval) remoteEvalGeneration.incrementAndFetch() else 0L
            performNetworkRound(remoteEvalPayloadProvider(), generation)
        } ?: run {
            // Timed out: the dispatcher accepted the request but never invoked any callback.
            // Surface it as a network failure so remoteSourceFeaturesFetchResult leaves NoResultYet
            // and suspendFeature()'s bounded-retry path can escape instead of hanging forever.
            dispatch(FetchOutcome.Failed(GBError(Exception("Feature fetch timed out")), source = Source.NETWORK))
            FetchResult.Failed
        }

    private fun serveCache(policy: FetchPolicy): CacheOutcome {
        // Remote-eval payloads are evaluated server-side against the CURRENT attributes/forced
        // variations, but our cache is keyed only by API key (FeatureCache_<apiKey>). Serving that
        // entry would let a previous user's evaluated payload leak to the next one after a
        // logout/login on the same key. Unlike JS/Python (which key the remote-eval cache by
        // selected attributes + forced variations + URL), this single-user KMM client bypasses the
        // cache entirely in remote-eval mode and always hits the network. See also the write-side
        // bypass in [handleNetworkModel].
        if (remoteEval) return CacheOutcome.ServedStaleOrMiss

        val entry = runCatching { readCache() }.getOrElse {
            GB.error("FeaturesViewModel: cache read failed", it)
            delegate.featuresFetchFailed(error = GBError(it), isRemote = false)
            return CacheOutcome.ServedStaleOrMiss
        } ?: return CacheOutcome.ServedStaleOrMiss

        val (model, cachedAt) = entry
        // remoteEval already returned above, so this path is local-eval only.
        val decoded = decoder.decode(model)

        // Freshness gating applies only to plain CacheFirst GETs; remote-eval and ForceNetwork always
        // refetch, so their cache is treated as (non-authoritative) STALE and revalidated. All zone
        // logic (inner window, hard ceiling, backward-compat) lives in CachePolicy.
        val gated = policy == FetchPolicy.CacheFirst && !remoteEval && cachedAt != null
        val age = cachedAt?.let { Clock.System.now().toEpochMilliseconds() - it }
        val zone = if (gated && age != null) cachePolicy.classify(age) else CacheZone.STALE

        // EXPIRED (Zone 3): do not surface stale data — carry it only as the on-error fallback so
        // evaluation never sees data past its freshness ceiling while the network is reachable.
        if (zone == CacheZone.EXPIRED) return CacheOutcome.Expired(decoded)

        val fresh = zone == CacheZone.FRESH

        val outcome = outcomeOf(payload = decoder.decode(m = model), source = Source.CACHE, authoritative = fresh)
        dispatch(outcome = outcome)

        // Only treat the cache as authoritative (and skip the network) when it actually
        // yielded a usable payload. A fresh-but-undecodable/empty cache degrades to
        // FetchOutcome.Failed above (both features and savedGroups null) — returning false
        // then lets fetchFeatures() fall through to the network instead of silently serving
        // nothing for the whole freshness window.
        return if (fresh && outcome is FetchOutcome.Ready) CacheOutcome.ServedFresh else CacheOutcome.ServedStaleOrMiss
    }

    /**
     * Fire-and-forget network fetch behind the cache-aware [fetchFeatures]. Starts a *fresh*
     * [performNetworkRound] (never joins the coalesced in-flight request, so a state-change-driven
     * fetch — e.g. a remote-eval refresh after an attribute change — always sends the current
     * payload) and reports the result through the delegate. Not timeout-bounded (that is
     * [awaitRefresh]/[runNetworkRound]'s job); observe completion via the refresh handler.
     */
    private fun fetchFromNetwork(payload: GBRemoteEvalParams?, onErrorFallback: DecodedPayload?) {
        val generation = if (remoteEval) remoteEvalGeneration.incrementAndFetch() else 0L
        coroutineScope.launch { performNetworkRound(payload, generation, onErrorFallback) }
    }

    /**
     * Applies a freshly fetched payload: decode/decrypt → onPayloadReady (sticky-bucket refresh) →
     * persist to cache → dispatch the resulting [FetchOutcome] to [delegate]. Returns that same
     * outcome so the SSE [autoRefreshFeatures] flow can emit from the single authoritative verdict
     * instead of decoding a second time.
     *
     * @param generation the remote-eval round token, or null for the non-remote GET / SSE path (no fence).
     * @param persistToCache whether a successful payload may be written to [cachingLayer]. False for
     *   remote-eval responses: they are evaluated per current attributes while the cache is keyed only
     *   by API key, so persisting one would leak it to the next user on the same key (see the
     *   read-side bypass in [serveCache]).
     * @return the dispatched [FetchOutcome] (Ready or Failed), or null when a newer remote-eval
     *   generation superseded this round during the onPayloadReady suspend — the caller then maps
     *   the round to [FetchResult.Superseded] and nothing is committed.
     */
    private suspend fun handleNetworkModel(
        model: FeaturesDataModel,
        generation: Long? = null,
        persistToCache: Boolean = true
    ): FetchOutcome? {
       return try {
            // Decode/decrypt the payload BEFORE the sticky-bucket refresh, mirroring the reference
            // TS SDK (decryptPayload -> refreshStickyBuckets). For an encrypted payload the raw
            // model has features == null, so deriveStickyBucketIdentifierAttributes would fall back
            // to the context features — empty on a cold start — and derive sticky identifiers from
            // nothing, re-bucketing the user. Handing onPayloadReady the decoded payload fixes that.
            val decoded = decoder.decode(model)
            delegate.onPayloadReady(
                model.copy(
                    features = decoded.features,
                    encryptedFeatures = null,
                    savedGroups = decoded.savedGroups,
                    encryptedSavedGroups = null,
                )
            )

            // Final payload-commit fence. onPayloadReady above suspends (sticky-bucket refresh) and
            // the generation is bumped outside applyMutex (incrementAndFetch), so a newer round may
            // have superseded us while we were suspended. Bail before writing the cache / committing
            // features / reporting Success, so a stale round never overwrites the fresher state nor
            // reports Success. The newer round (already queued on applyMutex or still in flight)
            // commits its own payload. Skipped on the non-remote GET path (generation == null).
            if (generation != null && generation != remoteEvalGeneration.load()) {
                return null
            }

            if (cachingEnabled && persistToCache) {
                runCatching { writeCache(model) }
                    .onFailure {
                        GB.error(
                            errorMessage = "FeaturesViewModel: cache write failed, features still applied",
                            throwable = it
                        )
                    }
            }

            outcomeOf(payload = decoded, source = Source.NETWORK).also { dispatch(it) }
        } catch (error: Throwable) {
            GB.error(
                errorMessage = "FeaturesViewModel: failed to process remote features payload",
                throwable = error
            )

            FetchOutcome.Failed(GBError(error), source = Source.NETWORK).also { dispatch(it) }
        }
    }

    private fun outcomeOf(payload: DecodedPayload, source: Source, authoritative: Boolean = true): FetchOutcome =
        if (payload.features == null && payload.savedGroups == null)
            FetchOutcome.Failed(GBError(Exception()), source)
        else FetchOutcome.Ready(payload, source, authoritative = authoritative)

    private fun dispatch(outcome: FetchOutcome) = when (outcome) {
        is FetchOutcome.Ready -> {
            outcome.payload.features?.let {
                delegate.featuresFetchedSuccessfully(
                    features = it,
                    isRemote = outcome.authoritative
                )
            }
            outcome.payload.savedGroups?.let {
                delegate.savedGroupsFetchedSuccessfully(
                    savedGroups = it,
                    isRemote = outcome.authoritative
                )
            }
        }

        is FetchOutcome.Failed -> {
            delegate.featuresFetchFailed(
                error = outcome.error,
                isRemote = outcome.source == Source.NETWORK
            )
        }

        is FetchOutcome.NotModified -> {
            delegate.featuresNotModified()
        }
    }

    private fun readCache(): Pair<FeaturesDataModel, Long?>? {
        val s = cachingLayer
            .getData(
                fileName = cacheKey,
                serializer = SerializableFeaturesDataModel.serializer()
            ) ?: return null
        return s.gbDeserialize() to s.cachedAt
    }

    private fun writeCache(model: FeaturesDataModel) = cachingLayer.putData(
        fileName = cacheKey,
        content = model.gbSerialize().copy(cachedAt = Clock.System.now().toEpochMilliseconds()),
        serializer = SerializableFeaturesDataModel.serializer()
    )
}
