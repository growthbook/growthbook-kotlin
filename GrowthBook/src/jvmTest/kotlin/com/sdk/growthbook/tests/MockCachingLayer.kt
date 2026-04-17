package com.sdk.growthbook.tests

import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Mock CachingLayer for tests.
 *
 * @param initialContent  pre-loaded JsonElement returned by getContent (null = empty cache)
 * @param throwOnGet      if true, getContent throws to simulate a corrupted/unreadable cache
 */
internal class MockCachingLayer(
    private val initialContent: JsonElement? = null,
    private val throwOnGet: Boolean = false,
) : CachingLayer {

    var savedContent: JsonElement? = null
        private set

    override fun getContent(fileName: String): JsonElement? {
        if (throwOnGet) throw Exception("Cache read error")
        return initialContent
    }

    override fun saveContent(fileName: String, content: JsonElement) {
        savedContent = content
    }

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /**
         * Builds a JsonElement that represents a cached FeaturesDataModel
         * by deserializing the given raw API response string and re-encoding it.
         */
        fun fromApiResponse(rawJson: String): MockCachingLayer {
            val serializable = json.decodeFromString(
                SerializableFeaturesDataModel.serializer(), rawJson
            )
            val element = Json.encodeToJsonElement(
                SerializableFeaturesDataModel.serializer(), serializable
            )
            return MockCachingLayer(initialContent = element)
        }
    }
}
