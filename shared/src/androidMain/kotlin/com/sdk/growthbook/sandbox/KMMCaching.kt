package com.sdk.growthbook.sandbox

import android.app.Activity
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileInputStream

actual object CachingImpl {
    actual fun getLayer() : CachingLayer {
        return CachingAndroid()
    }
}

class CachingAndroid : CachingLayer {

    companion object{
        var context : Context? = null
    }

    val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    override fun saveContent(fileName: String, content: JsonElement){
        val file = getTargetFile(fileName)

        if (file != null) {
            if (file.exists()) {
                file.delete()
            }

            file.createNewFile()

            val json = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }.encodeToString(content)

            file.appendText(json)
        }


    }
    override fun getContent(fileName: String) : JsonElement?{

        val file = getTargetFile(fileName)

        if (file != null && file.exists()) {
            val inputAsString = FileInputStream(file).bufferedReader().use { it.readText() }
            return json.decodeFromString(inputAsString)
        }

        return null

    }

    fun getTargetFile(fileName: String) : File? {
        if (context != null) {
            val path = context!!.getFilesDir()
            val letDirectory = File(path, "Sandbox-KMM")
            letDirectory.mkdirs()
            var targetFileName = fileName
            if (fileName.endsWith(".txt", true)) {
                targetFileName = fileName.removeSuffix(".txt")
            }
            return File(letDirectory, targetFileName + ".txt")
        }
        else return null
    }
}