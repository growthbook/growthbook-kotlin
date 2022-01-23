package com.sdk.growthbook.Features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import com.sdk.growthbook.Utils.Constants
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.decodeFromString

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val apiUrl = GrowthBookSDK.gbContext.hostURL + Constants.featurePath + GrowthBookSDK.gbContext.apiKey

    /**
     * Executes API Call to fetch features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit) {
        dispatcher.consumeGETRequest(apiUrl,
            onSuccess = { rawContent ->
                val result : FeaturesDataModel = dispatcher.JSONParser.decodeFromString(rawContent)
                result.also(success)
            },
            onError = {apiTimeError ->
                apiTimeError.also(failure)
            })
    }

}