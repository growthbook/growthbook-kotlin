package com.sdk.growthbook.sandbox

import android.content.Context
import com.sdk.growthbook.logger.GB
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Actual Implementation for Caching in Android - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingAndroid.instance
}

/**
 * Android Caching Layer
 */
class CachingAndroid : CachingLayer {

    /**
     * JSON Parser SetUp
     */
    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    private val fileLock = ConcurrentHashMap<String, Any>()

    /**
     * Save Content in Android App Specific Internal Memory
     */
    override fun saveContent(fileName: String, content: JsonElement) {
        synchronized(getLock(fileName)) {
            val file = getTargetFile(fileName) ?: return
            val jsonContents = json.encodeToString(JsonElement.serializer(), content)
            // Unique temp file per write: the per-file lock only serializes writers within
            // a single process, so a fixed temp name could still be interleaved by another
            // process (or a separately-constructed instance) writing the same cache file.
            // A unique temp keeps each write self-contained; rename is then last-writer-wins
            // of a complete payload, never a corrupt one.
            val tempFile = File.createTempFile(file.name, ".tmp", file.parentFile)
            try {
                FileOutputStream(tempFile).use { out ->
                    out.write(jsonContents.toByteArray())
                    out.fd.sync()
                }
                if (!tempFile.renameTo(file)) {
                    throw IOException("Failed to rename ${tempFile.name} to ${file.name}")
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
    }

    /**
     * Retrieve Content from Android App Specific Internal Memory
     */
    override fun getContent(fileName: String): JsonElement? {

        synchronized(getLock(fileName)) {
            val file = getTargetFile(fileName) ?: return null

            if (!file.exists()) {
                migrateLegacyCache(newFile = file)
            }

            if (!file.exists()) return null

            // Read File Contents
            return try {
                val inputAsString = file.readText()
                json.decodeFromString(JsonElement.serializer(), inputAsString)
            } catch (e: Exception) {
                GB.error("CachingAndroid: corrupt cache file '$fileName', deleting", e)
                file.delete()
                null
            }
        }
    }

    /**
     * Get Target File - with complete path in internal memory
     */
    fun getTargetFile(fileName: String): File? {
        if (filesDir == null) {
            return null
        }

        // Create Directory in Internal Memory
        val letDirectory = File(filesDir, "GrowthBook-KMM")
        letDirectory.mkdirs()
        var targetFileName = fileName
        if (fileName.endsWith(".txt", true)) {
            targetFileName = fileName.removeSuffix(".txt")
        }
        return File(letDirectory, "$targetFileName.txt")
    }

    private fun migrateLegacyCache(newFile: File) {
        if (!newFile.name.startsWith("FeatureCache")) return
        val legacyFile = getTargetFile("FeatureCache") ?: return
        if (!legacyFile.exists()) return
        if (!legacyFile.renameTo(newFile)) {
            GB.warning("CachingAndroid: failed to migrate legacy cache to " +
                "${newFile.name}, will retry on next launch")
        }
    }

    private fun getLock(fileName: String): Any {
        return fileLock.getOrPut(fileName) { Any() }
    }

    companion object {
        internal val instance: CachingAndroid = CachingAndroid()

        internal var filesDir: File? = null

        /**
         * Retrieves filesDir from context
         */
        fun consumeContext(context: Context) {
            filesDir = context.filesDir
        }
    }
}