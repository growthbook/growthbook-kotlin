package com.sdk.growthbook.Configs

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.sandbox.CachingManager

interface ConfigsFlowDelegate{
    fun configsFetchedSuccessfully()
    fun configsFetchFailed()
}

class ConfigsViewModel(val delegate: ConfigsFlowDelegate) {


    val manager = CachingManager(GrowthBookSDK.appInstance)

    fun fetchConfigs(){

        val dataModel = manager.getContent<ConfigsDataModel>(Constants.configCache)

        if (dataModel != null) {
            print(dataModel)
            this.delegate.configsFetchedSuccessfully()
        }

        val dataSource = ConfigsDataSource()
        dataSource.fetchConfig(success = {dataModel -> prepareConfigsData(dataModel = dataModel)},
        failure = {handleError()})


    }

    fun prepareConfigsData(dataModel : ConfigsDataModel) {

        manager.saveContent(Constants.configCache, dataModel)

        // TODO Map dataModel to UIModel
        // TODO Call Success Delegate Method
    }

    fun handleError(){
        // TODO Prepare Error UI Model
        // TODO Call Error Delegate Method
    }

}