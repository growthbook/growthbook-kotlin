package com.sdk.growthbook.Network

import com.sdk.growthbook.ApplicationDispatcher
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.Identity.decode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface NetworkDispatcher {
    val JSONParser : Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    fun consumeGETRequest(request: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit)
}

class CoreNetworkClient : NetworkDispatcher {

    val client = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        GlobalScope.launch(ApplicationDispatcher) {

            try {
                val result = client.get<HttpResponse>(request)
                onSuccess(result.receive())
            } catch (ex: Exception) {
                onError(ex)
            }

        }

    }

}