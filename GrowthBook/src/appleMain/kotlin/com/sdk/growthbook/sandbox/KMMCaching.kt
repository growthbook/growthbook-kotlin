package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Actual Implementation for Caching on Apple platforms (iOS/macOS) - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingIOS.instance
}

/**
 * Apple Caching Layer backed by NSFileManager.
 * Persists ONLY feature definitions under <Application Support>.
 */
@OptIn(ExperimentalForeignApi::class)
@Suppress("CAST_NEVER_SUCCEEDS")
internal class CachingIOS : CachingLayer {
    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /** Persist [content] atomically to the cache file for [fileName]. */
    override fun saveContent(fileName: String, content: JsonElement) {
        val path = getTargetFilePath(fileName) ?: return
        val jsonContents = json.encodeToString(JsonElement.serializer(), content)

        // writeToFile(atomically = true) writes to a temp file and atomically renames it,
        // matching the temp+rename corruption-safety of the Android implementation.
        val isOk = (jsonContents as NSString).writeToFile(
            path = path, atomically = true, encoding = NSUTF8StringEncoding, error = null
        )
        if (!isOk) {
            GB.warning("CachingIOS: failed to write cache file '$fileName'")
        }
    }

    /** Read cached content for [fileName]; null if absent, and self-heals (deletes) a corrupt file. */
    override fun getContent(fileName: String): JsonElement? {
        val path = getTargetFilePath(fileName) ?: return null
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) return null
        val inputString = NSString.stringWithContentsOfFile(
            path = path,
            encoding = NSUTF8StringEncoding,
            error = null
        ) ?: return null

        return try  {
            json.decodeFromString(JsonElement.serializer(), inputString)
        } catch (e: Exception) {
            GB.error("CachingIOS: corrupt cache file '$fileName', deleting", e)
            fileManager.removeItemAtPath(path, null)
            null
        }
    }

    /**
     * Resolves <Application Support>/GrowthBook-KMM/<name>.txt, creating the directory if needed.
     * Application Support is app-managed persistent storage (not purged by the OS), matching the
     * durability of Android's filesDir. The `.txt` suffix is normalised so callers may pass it or not.
     */
    private fun getTargetFilePath(fileName: String): String? {
        val caches = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: return null

        val directory = (caches as NSString).stringByAppendingPathComponent("GrowthBook-KMM")
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        val base = if (fileName.endsWith(".txt", true)) fileName.removeSuffix(".txt") else fileName
        return (directory as NSString).stringByAppendingPathComponent("$base.txt")
    }

    companion object {
        /** Shared instance so all callers serialise on one object, mirroring Android's getLayer(). */
        internal val instance = CachingIOS()
    }
}
