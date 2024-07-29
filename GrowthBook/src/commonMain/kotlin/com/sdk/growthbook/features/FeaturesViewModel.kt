package com.sdk.growthbook.features

import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData
import com.sdk.growthbook.utils.getSavedGroupFromEncryptedSavedGroup
import kotlinx.coroutines.flow.Flow
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
}

/**
 * View Model for Features
 */
internal class FeaturesViewModel(
    private val delegate: FeaturesFlowDelegate,
    private val dataSource: FeaturesDataSource,
    private val encryptionKey: String? = null,
) {

    /**
     * Caching Manager
     */
    private val manager = CachingImpl

    /**
     * Fetch Features
     */
    fun fetchFeatures(remoteEval: Boolean = false, payload: GBRemoteEvalParams? = null) {
        try {
            // Check for cache data
            val dataModel = manager.getLayer().getData(
                Constants.FEATURE_CACHE,
                FeaturesDataModel.serializer()
            )

            if (dataModel != null) {
                // Call Success Delegate with mention of data available but its not remote
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
        } catch (error: Throwable) {
            // Call Error Delegate with mention of data not available but its not remote
            this.delegate.featuresFetchFailed(GBError(error), false)
        }
        if (remoteEval) {
            dataSource.fetchRemoteEval(
                params = payload,
                success = { responseFeaturesDataModel ->
                    prepareFeaturesData(responseFeaturesDataModel.data)
                },
                failure = { error ->
                    this.delegate.featuresFetchFailed(GBError(error.exception), true)
                }
            )
        } else {
            dataSource.fetchFeatures(
                success = { dataModel ->
                    prepareFeaturesData(dataModel)
                },
                failure = { error ->
                    // Call Error Delegate with mention of data not available but its not remote
                    this.delegate.featuresFetchFailed(GBError(error), true)
                }
            )
        }
    }

    /**
     * Supportive method for automatically refresh features
     */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        return dataSource.autoRefresh(success = { dataModel ->
            prepareFeaturesData(dataModel = dataModel)
        }, failure = { error ->
            // Call Error Delegate with mention of data not available but its not remote
            this.delegate.featuresFetchFailed(GBError(error), true)
        })
    }

    /**
     * Cache API Response and push success event
     */
    private fun prepareFeaturesData(dataModel: FeaturesDataModel?) {
        var features = dataModel?.features
        var savedGroups = dataModel?.savedGroups
        val encryptedFeatures = dataModel?.encryptedFeatures
        val encryptedSavedGroups = dataModel?.encryptedSavedGroups

        try {
            if (dataModel != null) {
                manager.getLayer().putData(
                    fileName = Constants.FEATURE_CACHE,
                    content = dataModel,
                    serializer = FeaturesDataModel.serializer()
                )

                delegate.featuresAPIModelSuccessfully(dataModel)
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
            this.delegate.featuresFetchFailed(error = GBError(error), isRemote = true)
            return
        }
    }
}
