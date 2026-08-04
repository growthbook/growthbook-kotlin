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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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

    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

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
     */
    private val remoteEvalGeneration = AtomicLong(0)

    /** Opens an SSE connection and streams feature updates as a [Flow]. */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        sseController.start()
        return dataSource.autoRefresh(
            success = { coroutineScope.launch { handleNetworkModel(it) } },
            failure = { delegate.featuresFetchFailed(GBError(it), isRemote = true) }
        )
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
        if (serveCache(policy)) return // fresh cache -> authoritative, skip network
        fetchFromNetwork(payload)
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
        sseController.stop()
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
        generation: Long
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
                            if (generation == remoteEvalGeneration.load()) {
                                handleNetworkModel(it.data)
                                resumeOnce(FetchResult.Success)
                            } else {
                                // Stale: payload not applied → do not report Success. Always resume
                                // (no leak / no 30s timeout hang), but with a distinct result.
                                resumeOnce(FetchResult.Superseded)
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
                        dispatch(FetchOutcome.Failed(GBError(it), source = Source.NETWORK))
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

    private fun serveCache(policy: FetchPolicy): Boolean {
        val entry = runCatching { readCache() }.getOrElse {
            GB.error("FeaturesViewModel: cache read failed", it)
            delegate.featuresFetchFailed(error = GBError(it), isRemote = false)
            return false
        } ?: return false

        val (model, cachedAt) = entry
        val fresh = policy == FetchPolicy.CacheFirst && !remoteEval && cacheMaxAge != null
            && cachedAt != null
            && Clock.System.now().toEpochMilliseconds() - cachedAt < cacheMaxAge

        val outcome = outcomeOf(payload = decoder.decode(m = model), source = Source.CACHE, authoritative = fresh)
        dispatch(outcome = outcome)

        // Only treat the cache as authoritative (and skip the network) when it actually
        // yielded a usable payload. A fresh-but-undecodable/empty cache degrades to
        // FetchOutcome.Failed above (both features and savedGroups null) — returning false
        // then lets fetchFeatures() fall through to the network instead of silently serving
        // nothing for the whole freshness window.
        return fresh && outcome is FetchOutcome.Ready
    }

    /**
     * Fire-and-forget network fetch behind the cache-aware [fetchFeatures]. Starts a *fresh*
     * [performNetworkRound] (never joins the coalesced in-flight request, so a state-change-driven
     * fetch — e.g. a remote-eval refresh after an attribute change — always sends the current
     * payload) and reports the result through the delegate. Not timeout-bounded (that is
     * [awaitRefresh]/[runNetworkRound]'s job); observe completion via the refresh handler.
     */
    private fun fetchFromNetwork(payload: GBRemoteEvalParams?) {
        val generation = if (remoteEval) remoteEvalGeneration.incrementAndFetch() else 0L
        coroutineScope.launch { performNetworkRound(payload, generation) }
    }

    private suspend fun handleNetworkModel(model: FeaturesDataModel) {
        try {
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
            if (cachingEnabled) {
                runCatching { writeCache(model) }
                    .onFailure {
                        GB.error(
                            errorMessage = "FeaturesViewModel: cache write failed, features still applied",
                            throwable = it
                        )
                    }
            }

            dispatch(
                outcome = outcomeOf(
                    payload = decoded,
                    source = Source.NETWORK
                )
            )
        } catch (error: Throwable) {
            GB.error(
                errorMessage = "FeaturesViewModel: failed to process remote features payload",
                throwable = error
            )

            delegate.featuresFetchFailed(error = GBError(error), isRemote = true)
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
