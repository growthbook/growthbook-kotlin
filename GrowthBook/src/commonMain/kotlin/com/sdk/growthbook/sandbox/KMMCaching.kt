package com.sdk.growthbook.sandbox

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Interface for Caching Layer
 */
internal interface CachingLayer {
    fun saveContent(fileName: String, content: JsonElement) {
    }

    fun getContent(fileName: String): JsonElement? {
        return null
    }
}

/**
 * Default Implementation for Caching Layer Interface methods
 */
internal inline fun <reified T> CachingLayer.getData(fileName: String): @Serializable T? {
    val content = getContent(fileName)
    return content?.let { Json.decodeFromJsonElement<T>(it) }
}

/**
 * Default Implementation for Caching Layer Interface methods
 */
internal inline fun <reified T> CachingLayer.putData(fileName: String, content: @Serializable T) {
    val jsonContent = Json.encodeToJsonElement(content)
    saveContent(fileName, jsonContent)
}

/**
 * Expectation of Implementation of Caching Layer in respective Library - Android, iOS, JS
 */
internal expect object CachingImpl {
    fun getLayer(): CachingLayer
}