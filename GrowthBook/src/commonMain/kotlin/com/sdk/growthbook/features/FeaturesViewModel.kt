package com.sdk.growthbook.features

import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.SSEConnectionController
import com.sdk.growthbook.utils.getSavedGroupFromEncryptedSavedGroup
import com.sdk.growthbook.logger.GB
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.JsonObject

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
 * View Model for Features
 */
internal class FeaturesViewModel(
    private val delegate: FeaturesFlowDelegate,
    private val dataSource: FeaturesDataSource,
    private val encryptionKey: String? = null,
    private val cachingEnabled: Boolean,
    private val cacheKey: String = Constants.FEATURE_CACHE,
    private val cachingLayer: CachingLayer = CachingImpl.getLayer(),
) {

    /**
     * SSEConnectionController for managing the lifecycle state
     */
    val sseController: SSEConnectionController
        get() = dataSource.sseController

    val decoder: FeaturePayloadDecoder = FeaturePayloadDecoder(encryptionKey)

    /**
     * Fetch Features
     */
    fun fetchFeatures(remoteEval: Boolean = false, payload: GBRemoteEvalParams? = null) {
        try {
            // Check for cache data
            val dataModel = getDataFromCache()
            if (dataModel != null) {
                // Call Success Delegate with mention of data available but its not remote
                handleFetchFeaturesWithoutRemoteEval(dataModel)
            }
        } catch (error: Throwable) {
            GB.error("FeaturesViewModel: cache read failed", error)
            this.delegate.featuresFetchFailed(GBError(error), false)
        }
        handleFetchFeaturesWithRemoteEval(remoteEval, payload)
    }

    private fun handleFetchFeaturesWithRemoteEval(
        remoteEval: Boolean,
        payload: GBRemoteEvalParams?
    ) {
        if (remoteEval) {
            dataSource.fetchRemoteEval(
                params = payload,
                success = { responseFeaturesDataModel ->
                    prepareFeaturesDataForRemoteEval(responseFeaturesDataModel.data)
                },
                failure = { error ->
                    this.delegate.featuresFetchFailed(GBError(error.exception), true)
                }
            )
        } else {
            dataSource.fetchFeatures(
                success = { dataModel ->
                    prepareFeaturesDataForRemoteEval(dataModel)
                },
                failure = { error ->
                    // Call Error Delegate with mention of data not available but its not remote
                    this.delegate.featuresFetchFailed(GBError(error), true)
                },
                onNotModified = {
                    this.delegate.featuresNotModified()
                }
            )
        }
    }

    private fun handleFetchFeaturesWithoutRemoteEval(dataModel: FeaturesDataModel) {
        dataModel.features?.let {
            this.delegate.featuresFetchedSuccessfully(
                features = it,
                isRemote = false
            )
        }
        dataModel.encryptedFeatures?.let { encryptedFeatures: String ->
            encryptionKey?.let { encryptionKey ->
                val features = getFeaturesFromEncryptedFeatures(
                    encryptedString = encryptedFeatures,
                    encryptionKey = encryptionKey,
                )
                features?.let {
                    this.delegate.featuresFetchedSuccessfully(
                        features = it,
                        isRemote = false
                    )
                }
            }
        }
    }

    private fun getDataFromCache(): FeaturesDataModel? {
        val dataModel = cachingLayer.getData(
            cacheKey,
            SerializableFeaturesDataModel.serializer()
        )
        return dataModel?.gbDeserialize()
    }

    /**
     * Supportive method for automatically refresh features
     */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        sseController.start()
        return dataSource.autoRefreshRaw().transform { resource ->
            when (resource) {
                is Resource.Error -> {
                    emit(resource)
                    delegate.featuresFetchFailed(GBError(resource.exception), true)
                }

                is Resource.Success -> {
                    val model = resource.data
                    val result = decoder.decodeToResult(model)
                    cacheAndNotify(model, result)
                    when (result) {
                        is FeaturesResult.Applied -> emit(Resource.Success(result.features))
                        is FeaturesResult.Failed -> emit(Resource.Error(Exception(result.error.errorMessage)))
                    }
                }
            }
        }
    }

    /**
     * Cache API Response and push success event
     */
    private fun prepareFeaturesDataForRemoteEval(dataModel: FeaturesDataModel?) {
        var features = dataModel?.features
        var savedGroups = dataModel?.savedGroups
        val encryptedFeatures = dataModel?.encryptedFeatures
        val encryptedSavedGroups = dataModel?.encryptedSavedGroups

        try {
            if (dataModel != null) {
                delegate.featuresAPIModelSuccessfully(dataModel)
                if (cachingEnabled) {
                    try {
                        putDataToCache(dataModel)
                    } catch (e: Throwable) {
                        GB.error("FeaturesViewModel: cache write failed, features still applied", e)
                    }
                }
                if (!features.isNullOrEmpty()) {
                    this.delegate.featuresFetchedSuccessfully(
                        features = features,
                        isRemote = true
                    )
                    return
                } else {
                    if (encryptedFeatures != null && encryptionKey != null) {
                        if (encryptionKey.isNotEmpty()) {
                            val crypto = DefaultCrypto()
                            features =
                                getFeaturesFromEncryptedFeatures(
                                    encryptedString = encryptedFeatures,
                                    encryptionKey = encryptionKey,
                                    subtleCrypto = crypto
                                ) ?: return

                            this.delegate.featuresFetchedSuccessfully(
                                features = features,
                                isRemote = true
                            )
                            return
                        } else {
                            features?.let {
                                this.delegate.featuresFetchedSuccessfully(
                                    features = features,
                                    isRemote = true
                                )
                                return
                            }
                        }
                    } else {
                        this.delegate.featuresFetchFailed(
                            error = GBError(Exception()),
                            isRemote = true
                        )
                        return
                    }
                }

                if (!savedGroups.isNullOrEmpty()) {
                    this.delegate.savedGroupsFetchedSuccessfully(
                        savedGroups = savedGroups,
                        isRemote = true
                    )
                } else {
                    if (encryptedSavedGroups != null && encryptionKey != null) {
                        if (encryptionKey.isNotEmpty()) {
                            val crypto = DefaultCrypto()
                            savedGroups =
                                getSavedGroupFromEncryptedSavedGroup(
                                    encryptedString = encryptedSavedGroups,
                                    encryptionKey = encryptionKey,
                                    subtleCrypto = crypto
                                ) ?: return

                            this.delegate.savedGroupsFetchedSuccessfully(
                                savedGroups = savedGroups,
                                isRemote = true
                            )
                            return
                        } else {
                            savedGroups?.let {
                                this.delegate.savedGroupsFetchedSuccessfully(
                                    savedGroups = savedGroups,
                                    isRemote = true
                                )
                                return
                            }
                        }
                    } else {
                        this.delegate.savedGroupsFetchFailed(
                            error = GBError(Exception()),
                            isRemote = true
                        )
                        return
                    }
                }
            }
        } catch (error: Throwable) {
            GB.error("FeaturesViewModel: failed to process remote features payload", error)
            this.delegate.featuresFetchFailed(error = GBError(error), isRemote = true)
            return
        }
    }

    private fun putDataToCache(dataModel: FeaturesDataModel) {
        cachingLayer.putData(
            fileName = cacheKey,
            content = dataModel.gbSerialize(),
            serializer = SerializableFeaturesDataModel.serializer()
        )
    }

    private fun cacheAndNotify(model: FeaturesDataModel, result: FeaturesResult) {
        when (result) {
            is FeaturesResult.Applied -> {
                delegate.featuresAPIModelSuccessfully(model)
                if (cachingEnabled) {
                    try {
                        putDataToCache(model)
                    } catch (e: Throwable) {
                        GB.error("FeaturesViewModel: cache write failed, features still applied", e)
                    }
                }

                this.delegate.featuresFetchedSuccessfully(
                    features = result.features,
                    isRemote = true)
                result.savedGroups?.let {
                    delegate.savedGroupsFetchedSuccessfully(it, isRemote = true)
                }
            }

            is FeaturesResult.Failed -> {
                delegate.featuresFetchFailed(result.error, isRemote = true)
            }
        }
    }
}
