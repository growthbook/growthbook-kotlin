package com.sdk.growthbook.features

import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.network.NetworkDispatcherWithNotModified
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.FeatureRefreshStrategy
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBFetchOutcome
import com.sdk.growthbook.utils.GBFetchStats
import com.sdk.growthbook.utils.GBFetchStatsHandler
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(
    private val dispatcher: NetworkDispatcher,
    private val gbContext: GBContext,
    private val gbOptions: GBOptions,
    val sseController: SSEConnectionController = SSEConnectionController(),
    private val onFetchStats: GBFetchStatsHandler? = null,
) {

    private val jsonParser: Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /**
     * Supportive method for getting url based on feature refresh strategy
     */
    private fun getEndpoint(
        featureRefreshStrategy: FeatureRefreshStrategy =
            FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ) = FeatureURLBuilder(gbOptions).buildUrl(
        gbContext.apiKey,
        featureRefreshStrategy
    )

    /**
     * Executes API Call to fetch features
     */
    fun fetchFeatures(
        success: (FeaturesDataModel) -> Unit,
        failure: (Throwable?) -> Unit,
        onNotModified: (() -> Unit)
    ) {
        val startedAt = TimeSource.Monotonic.markNow()
        // Reported before parsing, so the duration is the fetch and not the SDK's own decode work.
        // A throwing handler must not take the fetch down with it — its payload is telemetry.
        fun report(outcome: GBFetchOutcome, bytes: Int?) {
            val handler = onFetchStats ?: return
            try {
                handler.invoke(
                    GBFetchStats(
                        outcome = outcome,
                        durationMillis = startedAt.elapsedNow().inWholeMilliseconds,
                        payloadBytes = bytes,
                    )
                )
            } catch (t: Throwable) {
                GB.warning("fetch stats handler failed: $t")
            }
        }

        val onSuccess: (String) -> Unit = onSuccess@{ rawContent ->
            // encodeToByteArray() copies the whole payload — only measure when someone is listening.
            if (onFetchStats != null) {
                report(GBFetchOutcome.Success, bytes = rawContent.encodeToByteArray().size)
            }
            // A malformed body must reach `failure`, not escape the dispatcher's callback
            // (the OkHttp dispatcher invokes onSuccess outside any try). Mirrors autoRefreshRaw().
            val result = try {
                jsonParser.decodeFromString(
                    deserializer = SerializableFeaturesDataModel.serializer(),
                    string = rawContent
                )
            } catch (e: Exception) {
                failure(e)
                return@onSuccess
            }
            success.invoke(result.gbDeserialize())
        }
        val onError: (Throwable) -> Unit = { apiTimeError ->
            report(GBFetchOutcome.Failed, bytes = null)
            apiTimeError.also(failure)
        }

        if (dispatcher is NetworkDispatcherWithNotModified) {
            dispatcher.consumeGETRequestWithNotModified(
                request = getEndpoint(),
                onSuccess = onSuccess,
                onError = onError,
                onNotModified = {
                    report(GBFetchOutcome.NotModified, bytes = 0)
                    onNotModified()
                }
            )
        } else {
            dispatcher.consumeGETRequest(
                request = getEndpoint(),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    /**
     * Supportive method for automatically refresh features
     */
    fun autoRefreshRaw(): Flow<Resource<FeaturesDataModel>> =
        dispatcher.consumeSSEConnection(
            url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_EVENTS),
            sseController = sseController
        ).transform { resource ->
            when (resource) {
                is Resource.Success -> {
                    // Decode + deserialize can throw on a malformed SSE payload. Catch here and
                    // degrade to Resource.Error so the stream survives and the collector still gets
                    // featuresFetchFailed, instead of the exception terminating the Flow. emit stays
                    // outside the catch so a downstream CancellationException is not swallowed.
                    val featuresDataModel = try {
                        jsonParser.decodeFromString(
                            SerializableFeaturesDataModel.serializer(),
                            resource.data
                        ).gbDeserialize()
                    } catch (e: Exception) {
                        emit(Resource.Error(e))
                        return@transform
                    }
                    emit(Resource.Success(featuresDataModel))
                }
                is Resource.Error -> {
                    emit(resource)
                }
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
            // Attributes / forced features arrive as GBValue (or already-native) values. Convert
            // them to JsonElement here — at the SDK boundary that owns the serialization bridge —
            // so the injected dispatcher receives plain JSON. Otherwise the dispatcher's generic
            // Map.toJsonElement() falls through to value.toString() and ships garbage like
            // "GBNumber(value=8490047)" instead of 8490047, breaking server-side targeting.
            payload["attributes"] = params.attributes.mapValues { (_, value) ->
                if (value is GBValue) value.gbSerialize() else value
            }
            // The remote-eval API expects forcedFeatures as an array of [key, value] pairs
            // (mirroring sdk-js `Array.from(forcedFeatures)`), NOT a JSON object. Sending an
            // object makes the GrowthBook proxy reject the request with 400 Bad Request.
            payload["forcedFeatures"] = JsonArray(
                params.forcedFeatures.map { (key, value) ->
                    JsonArray(listOf(JsonPrimitive(key), value.gbSerialize()))
                }
            )
            payload["forcedVariations"] = params.forcedVariations
        }

        if (gbContext.enableLogging) {
            GB.log("FeaturesDataSource: payload: $payload")
        }

        /**
         * Make POST request to server and send feature for further evaluation
         */
        dispatcher.consumePOSTRequest(
            url = getEndpoint(FeatureRefreshStrategy.SERVER_SENT_REMOTE_FEATURE_EVAL),
            bodyParams = payload,
            onSuccess = onSuccess@{ rawContent ->
                // Same guarded decode as fetchFeatures: a malformed body must reach `failure`,
                // not escape the dispatcher's callback.
                val featureDataModel = try {
                    jsonParser.decodeFromString(
                        deserializer = SerializableFeaturesDataModel.serializer(),
                        string = rawContent
                    )
                } catch (e: Exception) {
                    failure(Resource.Error(e))
                    return@onSuccess
                }
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