package com.sdk.growthbook.Features

import com.sdk.growthbook.Configs.ConfigsDataModel
import com.sdk.growthbook.Configs.ConfigsDataSource
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.sandbox.CachingManager


interface FeaturesFlowDelegate{
    fun featuresFetchedSuccessfully()
    fun featuresFetchFailed()
}

class FeaturesViewModel(val delegate: FeaturesFlowDelegate) {

    val manager = CachingManager(GrowthBookSDK.appInstance)

    fun fetchFeatures(){
        val dataModel = manager.getContent<FeaturesDataModel>(Constants.featureCache)

        if (dataModel != null) {
            print(dataModel)
            this.delegate.featuresFetchedSuccessfully()
        }
        val dataSource = FeaturesDataSource()
        dataSource.fetchFeatures(success = {dataModel -> prepareFeaturesData(dataModel = dataModel)},
            failure = {handleError()})


    }

    fun prepareFeaturesData(dataModel : FeaturesDataModel) {

        manager.saveContent(Constants.featureCache, dataModel)

        // TODO Map dataModel to UIModel
        // TODO Call Success Delegate Method
    }

    fun handleError(){
        // TODO Prepare Error UI Model
        // TODO Call Error Delegate Method
    }

}