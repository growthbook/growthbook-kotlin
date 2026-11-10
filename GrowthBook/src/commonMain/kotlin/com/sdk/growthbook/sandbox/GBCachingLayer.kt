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
 *
 * Both methods are synchronous and are called on the caller's thread — including the SDK's
 * initialization path, which reads the cache before any coroutine is started. Back it with a
 * synchronous key/value store; if yours is asynchronous, hydrate it into memory up front and
 * serve these calls from there.
 *
 * Neither method should throw. The SDK does not guard the call itself, so an exception escaping an
 * implementation propagates into whatever triggered the access — including `initialize()`. Report
 * failure by no-op'ing a save and returning null from a read: a missing cache entry is an ordinary
 * situation the SDK recovers from by fetching.
 */
interface GBCachingLayer {
    /**
     * Persists [content] under [fileName], replacing any previous value for that key. Called after
     * a successful fetch, and repeatedly over a session — keep it cheap, and do not assume a file
     * system: [fileName] is a key, not a path.
     */
    fun saveContent(fileName: String, content: String)

    /**
     * Returns the value stored under [fileName], or null when there is none. Return the string
     * exactly as it was saved — it is parsed as JSON, so any re-encoding or truncation makes the
     * SDK treat the entry as a miss.
     */
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
