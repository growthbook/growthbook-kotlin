package com.sdk.growthbook.Configs

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBOverrides
import com.sdk.growthbook.sandbox.CachingManager

internal interface ConfigsFlowDelegate{
    fun configsFetchedSuccessfully(configs: GBOverrides, isRemote: Boolean)
    fun configsFetchFailed(error: GBError, isRemote: Boolean)
}

internal class ConfigsViewModel(val delegate: ConfigsFlowDelegate, val dataSource : ConfigsDataSource = ConfigsDataSource()) {

    val manager = CachingManager(GrowthBookSDK.appInstance)

    fun fetchConfigs(){

        try {
            val dataModel = manager.getContent<ConfigsDataModel>(Constants.configCache)

            if (dataModel != null) {
                this.delegate.configsFetchedSuccessfully(configs = dataModel.overrides, isRemote = false)
            }
        } catch (error : Throwable){
            delegate.configsFetchFailed(error as GBError, false)
        }

        dataSource.fetchConfig(success = {dataModel -> prepareConfigsData(dataModel = dataModel)},
        failure = {error -> delegate.configsFetchFailed(error as GBError, true)})


    }

    fun prepareConfigsData(dataModel : ConfigsDataModel) {
        manager.saveContent(Constants.configCache, dataModel)
        delegate.configsFetchedSuccessfully(configs = dataModel.overrides, isRemote = true)
    }

}