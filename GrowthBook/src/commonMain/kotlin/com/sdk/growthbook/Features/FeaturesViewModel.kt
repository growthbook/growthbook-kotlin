package com.sdk.growthbook.Features

import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData


internal interface FeaturesFlowDelegate{
    fun featuresFetchedSuccessfully(features : GBFeatures, isRemote: Boolean)
    fun featuresFetchFailed(error: GBError, isRemote: Boolean)
}

internal class FeaturesViewModel(val delegate: FeaturesFlowDelegate, val dataSource : FeaturesDataSource = FeaturesDataSource()) {

    val manager = CachingImpl

    fun fetchFeatures(){

        try {
            val dataModel = manager.getLayer().getData<FeaturesDataModel>(Constants.featureCache)

            if (dataModel != null) {
                this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = false)
            }
        } catch (error : Throwable){
            this.delegate.featuresFetchFailed(GBError(error), false)
        }

        dataSource.fetchFeatures(success = {dataModel -> prepareFeaturesData(dataModel = dataModel)},
            failure = {error ->  this.delegate.featuresFetchFailed(GBError(error), true)})


    }

    fun prepareFeaturesData(dataModel : FeaturesDataModel) {
        manager.getLayer().putData(Constants.featureCache, dataModel)
        this.delegate.featuresFetchedSuccessfully(features = dataModel.features, isRemote = true)
    }

}