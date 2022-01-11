package com.comllc.growthbook.Configs

import com.comllc.cachinglibrary_kmm.sandbox.SandboxFileManager
import com.comllc.cachinglibrary_kmm.sandbox.saveSandbox
import com.comllc.growthbook.GrowthBookSDK
import com.comllc.growthbook.Utils.Constants
import com.comllc.growthbook.model.GBExperiment
import com.comllc.growthbook.model.GBExperimentOverride
import com.comllc.growthbook.sandbox.CachingManager
import kotlin.coroutines.coroutineContext

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