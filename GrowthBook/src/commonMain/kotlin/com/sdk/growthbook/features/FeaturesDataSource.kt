package com.sdk.growthbook.features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import com.sdk.growthbook.Utils.FeatureRefreshStrategy
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.Utils.Resource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(private val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val JSONParser: Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    private fun getEndpoint(
        featureRefreshStrategy: FeatureRefreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ) =
        FeatureURLBuilder().buildUrl(
            GrowthBookSDK.gbContext.hostURL,
            GrowthBookSDK.gbContext.apiKey,
            featureRefreshStrategy
        )

    /**
     * Executes API Call to fetch features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ) {
        dispatcher.consumeGETRequest(getEndpoint(),
            onSuccess = { rawContent ->
                val result = JSONParser.decodeFromString(
                    deserializer = FeaturesDataModel.serializer(),
                    string = rawContent
                )
                result.also(success)
            },
            onError = { apiTimeError ->
                apiTimeError.also(failure)
            })
    }
    @DelicateCoroutinesApi
    fun autoRefresh(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ): Flow<Resource<GBFeatures?>> = dispatcher.consumeSSEConnection(
        url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_EVENTS),
    ).transform { resource ->
        if (resource is Resource.Success) {
            val featuresDataModel = JSONParser.decodeFromString<FeaturesDataModel>(resource.data)
            val gbFeatures = featuresDataModel.features
            emit(Resource.Success(gbFeatures))
            featuresDataModel.also(success)
        } else if (resource is Resource.Error) {
            emit(resource)
            resource.exception.also(failure)
        }
    }
}