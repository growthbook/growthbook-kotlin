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
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
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
    private val delegate: FeaturesFlowDelegate,
    private val dataSource: FeaturesDataSource,
    private val encryptionKey: String? = null,
    private val cachingEnabled: Boolean,
    private val cacheKey: String = Constants.FEATURE_CACHE,
    private val cachingLayer: CachingLayer = CachingImpl.getLayer(),
    private val cacheMaxAge: Long? = null,
    private val decoder: FeaturePayloadDecoder = FeaturePayloadDecoder(encryptionKey)
) {

    /**
     * SSEConnectionController for managing the lifecycle state
     */
    val sseController: SSEConnectionController
        get() = dataSource.sseController

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
