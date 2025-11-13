package com.sdk.growthbook.utils

import java.util.Base64

internal actual fun decodeBase64(base64: String): ByteArray {
    return Base64.getDecoder().decode(base64)
}

