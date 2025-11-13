package com.sdk.growthbook.utils

internal actual fun decodeBase64(base64: String): ByteArray {
    val cleaned = base64.replace(Regex("[^A-Za-z0-9+/=]"), "")
    val decoded = js("atob")(cleaned) as String
    return decoded.encodeToByteArray().also {
        // Convert from char codes to byte array
        for (i in it.indices) {
            it[i] = decoded[i].code.toByte()
        }
    }
}

