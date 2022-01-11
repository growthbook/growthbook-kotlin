package com.sdk.growthbook.Features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import kotlinx.serialization.decodeFromString

internal class FeaturesDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

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