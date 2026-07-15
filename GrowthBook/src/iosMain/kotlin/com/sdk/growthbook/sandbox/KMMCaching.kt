@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

/**
 * Actual Implementation for Caching in iOS - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingIOS.instance
}

/**
 * iOS Caching Layer - persists the feature-flag cache to the app Caches directory.
 * Shared across iosArm64 / iosX64 / iosSimulatorArm64 via the iosMain source set.
 */
internal class CachingIOS : CachingLayer {

    /**
     * JSON Parser SetUp
     */
    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /**
     * Save Content in the iOS app Caches directory
     */
    override fun saveContent(fileName: String, content: JsonElement) {
        val fileUrl = targetFileUrl(fileName) ?: return
        val jsonContents = json.encodeToString(JsonElement.serializer(), content)
        // atomically = true writes to a temp file then renames it into place, so a
        // concurrent reader never observes a partially written payload.
        val ok = NSString.create(string = jsonContents).writeToURL(
            url = fileUrl,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
        if (!ok) {
            GB.warning("CachingIOS: failed to write cache file '$fileName'")
        }
    }

    /**
     * Retrieve Content from the iOS app Caches directory
     */
    override fun getContent(fileName: String): JsonElement? {
        val fileUrl = targetFileUrl(fileName) ?: return null

        val inputAsString = NSString.stringWithContentsOfURL(
            url = fileUrl,
            encoding = NSUTF8StringEncoding,
            error = null
        ) ?: return null

        return try {
            json.decodeFromString(JsonElement.serializer(), inputAsString)
        } catch (e: Exception) {
            GB.error("CachingIOS: corrupt cache file '$fileName', deleting", e)
            NSFileManager.defaultManager.removeItemAtURL(fileUrl, error = null)
            null
        }
    }

    /**
     * Target file URL inside the Caches/GrowthBook-KMM directory, creating the
     * directory if needed. Returns null if the Caches directory is unavailable.
     */
    private fun targetFileUrl(fileName: String): NSURL? {
        val fileManager = NSFileManager.defaultManager
        val cachesDir = fileManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        ) ?: return null

        val directory = cachesDir.URLByAppendingPathComponent("GrowthBook-KMM", isDirectory = true)
            ?: return null
        fileManager.createDirectoryAtURL(
            url = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        var targetFileName = fileName
        if (fileName.endsWith(".txt", ignoreCase = true)) {
            targetFileName = fileName.removeSuffix(".txt")
        }
        return directory.URLByAppendingPathComponent("$targetFileName.txt")
    }

    companion object {
        internal val instance: CachingIOS = CachingIOS()
    }
}