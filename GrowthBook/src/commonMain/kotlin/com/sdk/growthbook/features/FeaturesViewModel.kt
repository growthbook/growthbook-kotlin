package com.sdk.growthbook.features

import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData
import kotlinx.coroutines.DelicateCoroutinesApi

/**
 * Interface for Feature API Completion Events
 */
internal interface FeaturesFlowDelegate{
    fun featuresFetchedSuccessfully(features : GBFeatures, isRemote: Boolean)
    fun featuresFetchFailed(error: GBError, isRemote: Boolean)
}

/**
 * View Model for Features
 */
internal class FeaturesViewModel(val delegate: FeaturesFlowDelegate, val dataSource : FeaturesDataSource = FeaturesDataSource()) {

    /**
     * Caching Manager
     */
    val manager = CachingImpl

    /**
     * Fetch Features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures(){

        try {
            // Check for cache data
            val dataModel = manager.getLayer().getData<FeaturesDataModel>(Constants.featureCache)

            if (dataModel != null) {
                // Call Success Delegate with mention of data available but its not remote
                this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = false)
            }
        } catch (error : Throwable){
            // Call Error Delegate with mention of data not available but its not remote
            this.delegate.featuresFetchFailed(GBError(error), false)
        }

        dataSource.fetchFeatures(success = {dataModel -> prepareFeaturesData(dataModel = dataModel)},
            failure = { error ->
                // Call Error Delegate with mention of data not available but its not remote
                this.delegate.featuresFetchFailed(GBError(error), true)
            })


    }

    /**
     * Cache API Response and push success event
     */
    fun prepareFeaturesData(dataModel : FeaturesDataModel) {
        manager.getLayer().putData(Constants.featureCache, dataModel)
        // Call Success Delegate with mention of data available with remote
        this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = true)
    }

}