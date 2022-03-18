package com.sdk.growthbook.features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.network.CoreNetworkClient
import com.sdk.growthbook.network.NetworkDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.decodeFromString

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(
    private val dispatcher: NetworkDispatcher = CoreNetworkClient(),
    private val featureURLBuilder: FeatureURLBuilder = FeatureURLBuilder(),
    private val gbContext: GBContext = GrowthBookSDK.gbContext
) {

    private val apiUrl = featureURLBuilder.buildUrl(gbContext.hostURL, gbContext.apiKey)

    /**
     * Executes API Call to fetch features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ) {
        dispatcher.consumeGETRequest(apiUrl,
            onSuccess = { rawContent ->
                val result: FeaturesDataModel = dispatcher.JSONParser.decodeFromString(rawContent)
                result.also(success)
            },
            onError = { apiTimeError ->
                apiTimeError.also(failure)
            })
    }
}