package com.sdk.growthbook.utils

import platform.posix.memcpy
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters

internal actual fun decodeBase64(base64: String): ByteArray {
    val nsData = NSData.create(base64EncodedString = base64, options = NSDataBase64DecodingIgnoreUnknownCharacters)
        ?: throw IllegalArgumentException("Invalid Base64 string")
    
    val byteArray = ByteArray(nsData.length.toInt())
    if (nsData.length > 0u) {
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
    }
    return byteArray
}
