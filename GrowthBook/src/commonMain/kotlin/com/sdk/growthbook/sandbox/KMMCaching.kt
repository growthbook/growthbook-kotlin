package com.sdk.growthbook.sandbox

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

internal interface CachingLayer {
    fun saveContent(fileName: String, content: JsonElement){

    }
    fun getContent(fileName: String) : JsonElement?{
        return null
    }

}

internal inline fun <reified T> CachingLayer.getData(fileName: String) : @Serializable T? {
    val content = getContent(fileName)
    return content?.let { Json {  }.decodeFromJsonElement<T>(it) }
}

internal inline fun <reified T> CachingLayer.putData(fileName: String, content: @Serializable T) {
    val jsonContent = Json {  }.encodeToJsonElement(content)
    saveContent(fileName, jsonContent)
}

internal expect object CachingImpl {
    fun getLayer() : CachingLayer
}