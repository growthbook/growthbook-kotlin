package com.sdk.growthbook.Configs

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBOverrides
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.getData
import com.sdk.growthbook.sandbox.putData

internal interface ConfigsFlowDelegate{
    fun configsFetchedSuccessfully(configs: GBOverrides, isRemote: Boolean)
    fun configsFetchFailed(error: GBError, isRemote: Boolean)
}

internal class ConfigsViewModel(val delegate: ConfigsFlowDelegate, val dataSource : ConfigsDataSource = ConfigsDataSource()) {

    val manager = CachingImpl

    fun fetchConfigs(){

        try {
            val dataModel = manager.getLayer().getData<ConfigsDataModel>(Constants.configCache)

            if (dataModel != null) {
                this.delegate.configsFetchedSuccessfully(configs = dataModel.overrides, isRemote = false)
            }
        } catch (error : Throwable){
            delegate.configsFetchFailed(GBError(error), false)
        }

        dataSource.fetchConfig(success = {dataModel -> prepareConfigsData(dataModel = dataModel)},
        failure = {error -> delegate.configsFetchFailed(GBError(error), true)})


    }

    fun prepareConfigsData(dataModel : ConfigsDataModel) {
        manager.getLayer().putData(Constants.configCache, dataModel)
        delegate.configsFetchedSuccessfully(configs = dataModel.overrides, isRemote = true)
    }

}