package com.sdk.growthbook.sandbox

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Actual Implementation for Caching in Android - As expected in KMM
 */
internal actual object CachingImpl {
    actual fun getLayer(): CachingLayer {
        return CachingAndroid()
    }
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
            file.writeText(jsonContents)
        }
    }

    /**
     * Retrieve Content from Android App Specific Internal Memory
     */
    override fun getContent(fileName: String): JsonElement? {

        synchronized(getLock(fileName)) {
            val file = getTargetFile(fileName) ?: return null

            if (!file.exists()) return null

            // Read File Contents
            val inputAsString = file.readText()
            // return File Contents
            return json.decodeFromString(JsonElement.serializer(), inputAsString)
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

    private fun getLock(fileName: String): Any {
        return fileLock.getOrPut(fileName) { Any() }
    }

    companion object {
        internal var filesDir: File? = null

        /**
         * Retrieves filesDir from context
         */
        fun consumeContext(context: Context) {
            filesDir = context.filesDir
        }
    }
}