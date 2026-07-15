package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingJS.instance
}

internal class CachingJS : CachingLayer {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true; }

    // No prettyPrint: minify to conserve the ~5 MB localStorage quota
    // (the disk targets pretty-print only for human file inspection, which does not apply here).
    override fun saveContent(fileName: String, content: JsonElement) {
        val jsonContents = json.encodeToString(JsonElement.serializer(), content)
        try {
            localStorage.setItem(storageKey(fileName), jsonContents)
        } catch (e: Throwable) {
            // e.g. QuotaExceededError, or SecurityError when storage is disabled (private mode).
            GB.warning("CachingJS: failed to persist '$fileName' " + e.message)
        }
    }

    /**
     * Read cached content for [fileName]; null if absent, and self-heals (deletes) a corrupt entry.
     */
    override fun getContent(fileName: String): JsonElement? {
        val key = storageKey(fileName)
        val stored = localStorage.getItem(key) ?: return null

        return try {
            json.decodeFromString(JsonElement.serializer(), stored)
        } catch (e: Exception) {
            GB.error("CachingJS: corrupt cache entry '$fileName', deleting", e)
            localStorage.removeItem(key)
            null
        }
    }

    /**
     * Resolve the `localStorage` key `GrowthBook-KMM/<name>`, normalizing the `.txt` suffix
     * so callers may pass it or not. The `GrowthBook-KMM/` prefix namespaces our entries
     * to avoid clobbering host-app keys, mirroring the on-disk `GrowthBook-KMM/` folder.
     */
    private fun storageKey(fileName: String): String {
        val base = if (fileName.endsWith(
                suffix = ".txt",
                ignoreCase = true
            )
        ) fileName.removeSuffix(".txt") else fileName
        return "GrowthBook-KMM/$base"
    }

    companion object {

        /** Shared instance so all callers use one layer, mirroring the other targets' getLayer(). */
        val instance: CachingJS = CachingJS()
    }
}
