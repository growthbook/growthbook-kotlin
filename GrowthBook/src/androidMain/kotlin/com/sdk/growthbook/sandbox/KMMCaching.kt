package com.sdk.growthbook.sandbox

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileInputStream

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
class CachingAndroid: CachingLayer {

    /**
     * JSON Parser SetUp
     */
    private val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    /**
     * Save Content in Android App Specific Internal Memory
     */
    override fun saveContent(fileName: String, content: JsonElement) {
        val file = getTargetFile(fileName)

        if (file != null) {
            // If File already exists - delete that
            if (file.exists()) {
                file.delete()
            }

            // Create New File
            file.createNewFile()

            val jsonContents = json.encodeToString(JsonElement.serializer(), content)

            // Save contents in file
            file.appendText(jsonContents)
        }
    }

    /**
     * Retrieve Content from Android App Specific Internal Memory
     */
    override fun getContent(fileName: String): JsonElement? {

        val file = getTargetFile(fileName)

        if (file != null && file.exists()) {
            // Read File Contents
            val inputAsString = FileInputStream(file).bufferedReader().use { it.readText() }
            // return File Contents
            return json.decodeFromString(JsonElement.serializer(), inputAsString)
        }

        // Return null if file doesn't exist
        return null
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