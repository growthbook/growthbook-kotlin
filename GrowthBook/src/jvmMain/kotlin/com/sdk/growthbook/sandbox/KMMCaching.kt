package com.sdk.growthbook.sandbox

import kotlinx.serialization.json.JsonElement

internal actual object CachingImpl {
    actual fun getLayer(localEncryptionKey: String?): CachingLayer {
        return CachingJvm()
    }
}

internal class CachingJvm : CachingLayer {
    override fun saveContent(fileName: String, content: JsonElement){

    }
    override fun getContent(fileName: String) : JsonElement?{
        return null
    }
}
