package com.comllc.growthbook.Configs

import com.comllc.growthbook.ApplicationDispatcher
import com.comllc.growthbook.GrowthBookSDK
import com.comllc.growthbook.Utils.Constants
import com.comllc.growthbook.Network.CoreNetworkClient
import com.comllc.growthbook.Network.NetworkDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

class ConfigsDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val apiUrl = GrowthBookSDK.gbContext.url + Constants.configPath + GrowthBookSDK.gbContext.apiKey

    fun fetchConfig(
        success: (ConfigsDataModel) -> Unit, failure: (Throwable?) -> Unit) {

        dispatcher.consumeGETRequest(apiUrl,
            onSuccess = { httpResponse, rawContent ->
                val result : ConfigsDataModel = dispatcher.JSONParser.decodeFromString(rawContent)
                result.also(success)
            },
            onError = {apiTimeError ->
                apiTimeError.also(failure)
            })
    }

}