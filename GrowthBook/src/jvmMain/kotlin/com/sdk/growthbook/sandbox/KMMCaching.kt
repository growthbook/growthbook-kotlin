package com.sdk.growthbook.sandbox

import com.sdk.growthbook.logger.GB
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Actual Implementation for Caching on the JVM - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer = CachingJvm.instance
}

/**
 * JVM Caching Layer backed by java.io.
 *
 * Mirrors [CachingAndroid][com.sdk.growthbook.sandbox] on Android: writes atomically via a unique
 * temp file + fsync + rename, serializes access per file, and self-heals a corrupt cache file.
 * Persists ONLY feature definitions under `<baseDir>/GrowthBook-KMM/`.
 */
internal class CachingJvm : CachingLayer {

    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }
    private val fileLock = ConcurrentHashMap<String, Any>()

    /** Persist [content] to the cache file for [fileName] via a temp file + atomic rename. */
    override fun saveContent(fileName: String, content: JsonElement) {
        synchronized(getLock(fileName)) {
            val file = getTargetFile(fileName)
            val jsonContents = json.encodeToString(JsonElement.serializer(), content)
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
     * Read cached content for [fileName]; null if absent, and self-heals (deletes) a corrupt file
     */
    override fun getContent(fileName: String): JsonElement? {
        synchronized(getLock(fileName)) {
            // Reads never need to create the directory, so resolve the path without mkdirs()
            // to keep the initialize() cache-lookup path free of redundant filesystem calls.
            val file = resolveFile(fileName)
            if (!file.exists()) return null
            return try {
                val inputAsString = file.readText()
                json.decodeFromString(JsonElement.serializer(), inputAsString)
            } catch (e: Exception) {
                GB.error("CachingJvm: corrupt cache file '$fileName', deleting", e)
                file.delete()
                null
            }
        }
    }

    /**
     * Resolve `<baseDir>/GrowthBook-KMM/<name>.txt`, creating the directory. `.txt` is normalized.
     * Use this on the write path; reads should use [resolveFile] to avoid creating the directory.
     */
    fun getTargetFile(fileName: String): File =
        resolveFile(fileName).also { it.parentFile.mkdirs() }

    /** Resolve the cache file path for [fileName] without touching the filesystem. */
    private fun resolveFile(fileName: String): File {
        val letDirectory = File(baseDir, "GrowthBook-KMM")
        val targetFileName =
            if (fileName.endsWith(".txt", true)) fileName.removeSuffix(".txt") else fileName
        return File(letDirectory, "$targetFileName.txt")
    }

    private fun getLock(fileName: String): Any = fileLock.getOrPut(fileName) { Any() }

    companion object {
        internal val instance: CachingJvm = CachingJvm()

        /**
         * Base directory for the on-disk cache. Overridable (primarily for tests),
         * mirroring Android's `CachingAndroid.filesDir`. There is no framework-provided app
         * directory on the JVM, so it defaults to `<user.home>/.growthbook` (or
         * `<java.io.tmpdir>/.growthbook` when no home directory is set). The directory itself is
         * created lazily on the first write by [getTargetFile], not when this class loads.
         */
        internal var baseDir: File = defaultBaseDir()

        private fun defaultBaseDir(): File {
            val home = System.getProperty("user.home")
            val primary = if (!home.isNullOrBlank()) File(home, ".growthbook") else null
            return primary ?: File(System.getProperty("java.io.tmpdir"), ".growthbook")
        }
    }
}
