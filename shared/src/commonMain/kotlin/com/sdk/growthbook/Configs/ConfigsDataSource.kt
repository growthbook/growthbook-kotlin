package com.sdk.growthbook.Configs

import com.sdk.growthbook.ApplicationDispatcher
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

internal class ConfigsDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val apiUrl = GrowthBookSDK.gbContext.url + Constants.configPath + GrowthBookSDK.gbContext.apiKey

    fun fetchConfig(
        success: (ConfigsDataModel) -> Unit, failure: (Throwable?) -> Unit) {

        dispatcher.consumeGETRequest(apiUrl,
            onSuccess = { rawContent ->
                val result : ConfigsDataModel = dispatcher.JSONParser.decodeFromString(rawContent)
                result.also(success)
            },
            onError = {apiTimeError ->
                apiTimeError.also(failure)
            })
    }

}