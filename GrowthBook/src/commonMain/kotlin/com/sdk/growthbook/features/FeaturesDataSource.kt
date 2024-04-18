package com.sdk.growthbook.features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.FeatureRefreshStrategy
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.lang.Exception

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(private val dispatcher: NetworkDispatcher) {

    private val jsonParser: Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    private fun getEndpoint(
        featureRefreshStrategy: FeatureRefreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ) =
        GrowthBookSDK.gbContext.hostURL?.let {hostUrl ->
            GrowthBookSDK.gbContext.apiKey?.let { apiKey ->
                FeatureURLBuilder().buildUrl(
                    hostUrl,
                    apiKey,
                    featureRefreshStrategy
                )
            }
        }

    /**
     * Executes API Call to fetch features
     */
    @DelicateCoroutinesApi
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ) {
        dispatcher.consumeGETRequest(request = getEndpoint() ?: "",
            onSuccess = { rawContent ->
                val result = jsonParser.decodeFromString(
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
        url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_EVENTS) ?: "",
    ).transform { resource ->
        if (resource is Resource.Success) {
            val featuresDataModel = jsonParser.decodeFromString<FeaturesDataModel>(resource.data)
            val gbFeatures = featuresDataModel.features
            emit(Resource.Success(gbFeatures))
            featuresDataModel.also(success)
        } else if (resource is Resource.Error) {
            emit(resource)
            resource.exception.also(failure)
        }
    }

    fun fetchRemoteEval(
        params: GBRemoteEvalParams?,
        success: (Resource.Success<FeaturesDataModel>) -> Unit,
        failure: (Resource.Error) -> Unit
    ) {
        val payload: MutableMap<String, Any> = mutableMapOf()

        params?.let {
            payload["attributes"] = params.attributes
            payload["forcedFeatures"] = params.forcedFeatures
            payload["forcedVariations"] = params.forcedVariations
        }

        println(payload)

        dispatcher.consumePOSTRequest(
            url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_REMOTE_FEATURE_EVAL) ?: "",
            bodyParams = payload,
            onSuccess = { rawContent ->
                val featureDataModel = jsonParser.decodeFromString(
                    deserializer = FeaturesDataModel.serializer(),
                    string = rawContent
                )
                Resource.Success(featureDataModel).also(success)
            },
            onError = { error ->
                Resource.Error(Exception(error.message)).also(failure)
            }
        )
    }
}