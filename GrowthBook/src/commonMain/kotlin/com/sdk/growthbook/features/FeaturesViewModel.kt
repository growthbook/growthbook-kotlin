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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Interface for Feature API Completion Events
 */
internal interface FeaturesFlowDelegate {
    fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean)
    fun featuresAPIModelSuccessfully(model: FeaturesDataModel)
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
     * Scope that owns the coalesced refresh started by [awaitRefresh] (used by
     * suspendFeature() retries and [revalidate]). It must outlive any single
     * caller, so cancelling one caller never tears down a refresh shared with
     * others ([SupervisorJob] isolates failures too). Only lightweight coordination
     * runs here (mutex bookkeeping, continuation resumption); the actual HTTP runs
     * on the network dispatcher's own IO scope. Injectable so tests can supply a
     * deterministic dispatcher.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /**
     * SSEConnectionController for managing the lifecycle state
     */
    val sseController: SSEConnectionController
        get() = dataSource.sseController

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

    /** Opens an SSE connection and streams feature updates as a [Flow]. */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        sseController.start()
        return dataSource.autoRefresh(
            success = { handleNetworkModel(it) },
            failure = { delegate.featuresFetchFailed(GBError(it), isRemote = true) }
        )
    }

    /**
     * @param remoteEval evaluate features remotely via POST instead of a regular GET.
     * @param payload attributes/forced features sent with the remote-eval request;
     *   used only when [remoteEval] is true, ignored otherwise.
     * @param policy whether a fresh cache may satisfy this fetch or the network
     *   must always be hit; see [FetchPolicy].
     */
    fun fetchFeatures(
        remoteEval: Boolean = false,
        payload: GBRemoteEvalParams? = null,
        policy: FetchPolicy = FetchPolicy.CacheFirst
    ) {
        if (serveCache(remoteEval, policy)) return // fresh cache -> authoritative, skip network
        fetchFromNetwork(remoteEval, payload)
    }

    /**
     * Coalesced network refresh: concurrent callers share a single in-flight
     * GET instead of each firing their own. The first caller starts the request
     * and stores its [Deferred] in [inFlight]; callers arriving while it runs
     * await the same result. The slot is cleared once the request completes, so
     * the next round (e.g. a backoff retry) starts a fresh request.
     *
     * This is the network primitive beneath the cache-aware [fetchFeatures];
     * it always hits the network (GET only) and carries no [FetchPolicy].
     */
    suspend fun awaitRefresh(): FetchResult {
        val deferred = refreshMutex.withLock {
            inFlight ?: scope.async {
                try {
                    runNetworkGet()
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
     * Fire-and-forget: the network round runs on [scope]; observe completion via
     * the refresh handler rather than expecting features to be ready on return.
     */
    fun revalidate() {
        serveCache(remoteEval = false, policy = FetchPolicy.ForceNetwork)
        scope.launch { awaitRefresh() }
    }

    /** Bridges the callback-based GET into a suspend result for [awaitRefresh]. */
    private suspend fun runNetworkGet(): FetchResult =
        suspendCancellableCoroutine { cont ->
            dataSource.fetchFeatures(
                success = {
                    handleNetworkModel(it)
                    cont.resume(FetchResult.Success)
                },
                failure = {
                    dispatch(FetchOutcome.Failed(GBError(it), source = Source.NETWORK))
                    cont.resume(FetchResult.Failed)
                },
                onNotModified = {
                    dispatch(FetchOutcome.NotModified)
                    cont.resume(FetchResult.NotModified)
                }
            )
        }

    private fun serveCache(remoteEval: Boolean, policy: FetchPolicy): Boolean {
        val entry = runCatching { readCache() }.getOrElse {
            GB.error("FeaturesViewModel: cache read failed", it)
            delegate.featuresFetchFailed(error = GBError(it), isRemote = false)
            return false
        } ?: return false

        val (model, cachedAt) = entry
        val fresh = policy == FetchPolicy.CacheFirst && !remoteEval && cacheMaxAge != null
            && cachedAt != null
            && Clock.System.now().toEpochMilliseconds() - cachedAt < cacheMaxAge

        dispatch(
            outcome = FetchOutcome.Ready(
                payload = decoder.decode(m = model),
                source = Source.CACHE,
                authoritative = fresh
            )
        )
        return fresh
    }

    private fun fetchFromNetwork(remoteEval: Boolean, payload: GBRemoteEvalParams?) {
        if (remoteEval) dataSource.fetchRemoteEval(
            params = payload,
            success = { handleNetworkModel(it.data) },
            failure = {
                delegate.featuresFetchFailed(
                    error = GBError(it.exception),
                    isRemote = true
                )
            }
        ) else dataSource.fetchFeatures(
            success = { handleNetworkModel(it) },
            failure = { delegate.featuresFetchFailed(error = GBError(it), isRemote = true) },
            onNotModified = { dispatch(outcome = FetchOutcome.NotModified) }
        )
    }

    private fun handleNetworkModel(model: FeaturesDataModel) {
        try {
            delegate.featuresAPIModelSuccessfully(model)
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
                    payload = decoder.decode(model),
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

    private fun outcomeOf(payload: DecodedPayload, source: Source): FetchOutcome =
        if (payload.features == null && payload.savedGroups == null)
            FetchOutcome.Failed(GBError(Exception()), source)
        else FetchOutcome.Ready(payload, source, authoritative = true)

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
