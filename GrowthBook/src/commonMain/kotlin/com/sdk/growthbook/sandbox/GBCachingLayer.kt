package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Public, pluggable cache contract. Provide your own implementation
 * via [com.sdk.growthbook.GBSDKBuilder.setCachingLayer] to route GrowthBook's cached state
 * through your own storage (a shared KMP key/value store, encrypted storage, unified clear/reset, ...).
 *
 * Values are opaque JSON strings keyed by [fileName]; persist and return them verbatim
 */
interface GBCachingLayer {
    fun saveContent(fileName: String, content: String)
    fun getContent(fileName: String): String?
}

internal class GBCachingLayerAdapter(
    private val delegate: GBCachingLayer
) : CachingLayer {
    override fun saveContent(
        fileName: String,
        content: JsonElement
    ) {
        delegate.saveContent(fileName, content.toString())
    }

    override fun getContent(fileName: String): JsonElement? {
        val raw = delegate.getContent(fileName) ?: return null
        return try {
            Json.parseToJsonElement(raw)
        } catch (e: Exception) {
            GB.error("GBCachingLayerAdapter: error while getContent", e)
            null
        }
    }
}
