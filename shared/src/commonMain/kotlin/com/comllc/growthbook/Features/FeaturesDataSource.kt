package com.comllc.growthbook.Features

import com.comllc.growthbook.GrowthBookSDK
import com.comllc.growthbook.Utils.Constants
import com.comllc.growthbook.Network.CoreNetworkClient
import com.comllc.growthbook.Network.NetworkDispatcher
import kotlinx.serialization.decodeFromString

class FeaturesDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val apiUrl = GrowthBookSDK.gbContext.url + Constants.featurePath + GrowthBookSDK.gbContext.apiKey

    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit) {
        dispatcher.consumeGETRequest(apiUrl,
            onSuccess = { httpResponse, rawContent ->
                val result : FeaturesDataModel = dispatcher.JSONParser.decodeFromString(rawContent)
                result.also(success)
            },
            onError = {apiTimeError ->
                apiTimeError.also(failure)
            })
    }

}