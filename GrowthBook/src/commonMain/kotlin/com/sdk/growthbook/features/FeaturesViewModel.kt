package com.sdk.growthbook.features

import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.Crypto
import com.sdk.growthbook.Utils.DefaultCrypto
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.Utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.json.Json

/**
 * Interface for Feature API Completion Events
 */
internal interface FeaturesFlowDelegate {
    fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean)
    fun featuresFetchFailed(error: GBError, isRemote: Boolean)
}

/**
 * View Model for Features
 */
internal class FeaturesViewModel(
    private val delegate: FeaturesFlowDelegate,
    private val dataSource: FeaturesDataSource = FeaturesDataSource(),
    var encryptionKey: String?
) {

    /**
     * Caching Manager
     */
    private val manager = CachingImpl

    /**
     * Fetch Features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures() {

        try {
            // Check for cache data
            val dataModel = manager.getLayer().getData(
                Constants.featureCache,
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
            }
        } catch (error: Throwable) {
            // Call Error Delegate with mention of data not available but its not remote
            this.delegate.featuresFetchFailed(GBError(error), false)
        }

        dataSource.fetchFeatures(success = { dataModel -> prepareFeaturesData(dataModel = dataModel) },
            failure = { error ->
                // Call Error Delegate with mention of data not available but its not remote
                this.delegate.featuresFetchFailed(GBError(error), true)
            })
    }

    /**
     * Cache API Response and push success event
     */
    private fun prepareFeaturesData(dataModel: FeaturesDataModel) {
        manager.getLayer().putData(Constants.featureCache, dataModel)
        // Call Success Delegate with mention of data available with remote
        var features = dataModel.features
        val encryptedFeatures = dataModel.encryptedFeatures
        val crypto = DefaultCrypto()
        try {
            if (features != null && features.isNotEmpty()) {
                this.delegate.featuresFetchedSuccessfully(features = features, isRemote = true)
            } else if (encryptionKey != null && encryptedFeatures != null) {
                features =
                    getFeaturesFromEncryptedFeatures(encryptedFeatures, encryptionKey!!, crypto)
                features?.let {
                    this.delegate.featuresFetchedSuccessfully(features = features, isRemote = true)
                }
            }
        } catch (error: Throwable) {
            this.delegate.featuresFetchFailed(error = GBError(error), isRemote = true)
        }
    }
}