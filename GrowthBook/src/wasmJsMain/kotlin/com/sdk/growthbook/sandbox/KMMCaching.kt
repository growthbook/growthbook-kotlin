package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Actual Implementation for Caching on wasmJs (browser) - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingWasmJS.instance
}

/**
 * wasmJs Caching Layer backed by the browser `localStorage`.
 *
 * Identical in behaviour to [CachingJS][com.sdk.growthbook.sandbox] on the JS target: entries live
 * under a `GrowthBook-KMM/` key namespace, the `.txt` suffix is normalised, and a corrupt entry is
 * self-healed by deleting it. `localStorage` is synchronous string key/value storage, matching the
 * synchronous [CachingLayer] contract; wasmJs is single-threaded so no locking is needed. Requires a
 * browser runtime (the wasmJs target is configured `browser()`); there is no `localStorage` in Node.
 */
internal class CachingWasmJS : CachingLayer {

    // No prettyPrint: minify to conserve the ~5 MB localStorage quota.
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** Persist [content] under the namespaced key for [fileName]. Never throws. */
    override fun saveContent(fileName: String, content: JsonElement) {
        try {
            val jsonContents = json.encodeToString(JsonElement.serializer(), content)
            localStorage.setItem(storageKey(fileName), jsonContents)
        } catch (e: Throwable) {
            // setItem: QuotaExceededError, or SecurityError when storage is disabled (private mode).
            // encodeToString is inside the try too so a persist never throws (best-effort cache).
            GB.warning("CachingWasmJS: failed to persist '$fileName' " + e.message)
        }
    }

    /** Read cached content for [fileName]; null if absent, and self-heals (deletes) a corrupt entry. */
    override fun getContent(fileName: String): JsonElement? {
        val key = storageKey(fileName)
        // getItem itself can throw SecurityError when storage is disabled (private mode); treat
        // an unavailable store as a cache miss rather than an initialization failure.
        val stored = try {
            localStorage.getItem(key)
        } catch (e: Throwable) {
            GB.warning("CachingWasmJS: localStorage unavailable, treating '$fileName' as a cache miss " + e.message)
            null
        } ?: return null
        return try {
            json.decodeFromString(JsonElement.serializer(), stored)
        } catch (e: Exception) {
            GB.error("CachingWasmJS: corrupt cache entry '$fileName', deleting", e)
            // Best-effort self-heal; removeItem can throw in the same storage-disabled contexts.
            runCatching { localStorage.removeItem(key) }
            null
        }
    }

    /**
     * Resolve the `localStorage` key `GrowthBook-KMM/<name>`, normalising the `.txt` suffix so
     * callers may pass it or not. The `GrowthBook-KMM/` prefix namespaces our entries to avoid
     * clobbering host-app keys, mirroring the on-disk `GrowthBook-KMM/` folder.
     */
    private fun storageKey(fileName: String): String {
        val base = if (fileName.endsWith(".txt", true)) fileName.removeSuffix(".txt") else fileName
        return "GrowthBook-KMM/$base"
    }

    companion object {
        /** Shared instance so all callers use one layer, mirroring the other targets' getLayer(). */
        internal val instance: CachingWasmJS = CachingWasmJS()
    }
}
