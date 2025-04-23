package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.FeatureRefreshStrategy
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.Json

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(
    private val dispatcher: NetworkDispatcher,
    private val gbContext: GBContext,
) {

    private val jsonParser: Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /**
     * Supportive method for getting url based on feature refresh strategy
     */
    private fun getEndpoint(
        featureRefreshStrategy: FeatureRefreshStrategy =
            FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ) = FeatureURLBuilder().buildUrl(
        gbContext.hostURL,
        gbContext.apiKey,
        featureRefreshStrategy
    )

    /**
     * Executes API Call to fetch features
     */
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ) {
        dispatcher.consumeGETRequest(request = getEndpoint(),
            onSuccess = { rawContent ->
                val result = jsonParser.decodeFromString(
                    deserializer = SerializableFeaturesDataModel.serializer(),
                    string = rawContent
                )
                success.invoke(result.gbDeserialize())
            },
            onError = { apiTimeError ->
                apiTimeError.also(failure)
            })
    }

    /**
     * Supportive method for automatically refresh features
     */
    fun autoRefresh(
        success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
    ): Flow<Resource<GBFeatures?>> = dispatcher.consumeSSEConnection(
        url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_EVENTS),
    ).transform { resource ->
        if (resource is Resource.Success) {
            val serializableFeaturesDataModel = jsonParser.decodeFromString(
                SerializableFeaturesDataModel.serializer(), resource.data
            )
            val featuresDataModel = serializableFeaturesDataModel.gbDeserialize()

            val gbFeatures = featuresDataModel.features
            emit(Resource.Success(gbFeatures))
            featuresDataModel.also(success)
        } else if (resource is Resource.Error) {
            emit(resource)
            resource.exception.also(failure)
        }
    }

    /**
     * Method that make POST request to server for evaluate feature remotely
     */
    fun fetchRemoteEval(
        params: GBRemoteEvalParams?,
        success: (Resource.Success<FeaturesDataModel>) -> Unit,
        failure: (Resource.Error) -> Unit
    ) {
        val payload: MutableMap<String, Any> = mutableMapOf()

        /**
         * Create body for request
         */
        params?.let {
            payload["attributes"] = params.attributes
            payload["forcedFeatures"] = params.forcedFeatures
            payload["forcedVariations"] = params.forcedVariations
        }

        if (gbContext.enableLogging) {
            println(payload)
        }

        /**
         * Make POST request to server and send feature for further evaluation
         */
        dispatcher.consumePOSTRequest(
            url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_REMOTE_FEATURE_EVAL),
            bodyParams = payload,
            onSuccess = { rawContent ->
                val featureDataModel = jsonParser.decodeFromString(
                    deserializer = SerializableFeaturesDataModel.serializer(),
                    string = rawContent
                )
                success.invoke(
                    Resource.Success(
                        featureDataModel.gbDeserialize()
                    )
                )
            },
            onError = { error ->
                Resource.Error(Exception(error.message)).also(failure)
            }
        )
    }
}