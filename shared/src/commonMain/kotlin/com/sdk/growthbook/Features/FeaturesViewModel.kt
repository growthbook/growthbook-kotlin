package com.sdk.growthbook.Features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.sandbox.CachingManager


internal interface FeaturesFlowDelegate{
    fun featuresFetchedSuccessfully(features : GBFeatures, isRemote: Boolean)
    fun featuresFetchFailed(error: GBError, isRemote: Boolean)
}

internal class FeaturesViewModel(val delegate: FeaturesFlowDelegate, val dataSource : FeaturesDataSource = FeaturesDataSource()) {

    val manager = CachingManager(GrowthBookSDK.appInstance)

    fun fetchFeatures(){

        try {
            val dataModel = manager.getContent<FeaturesDataModel>(Constants.featureCache)

            if (dataModel != null) {
                this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = false)
            }
        } catch (error : Throwable){
            this.delegate.featuresFetchFailed(error as GBError, false)
        }

        dataSource.fetchFeatures(success = {dataModel -> prepareFeaturesData(dataModel = dataModel)},
            failure = {error ->  this.delegate.featuresFetchFailed(error as GBError, true)})


    }

    fun prepareFeaturesData(dataModel : FeaturesDataModel) {
        manager.saveContent(Constants.featureCache, dataModel)
        this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = true)
    }

}