package com.sdk.growthbook.utils

import android.util.Base64

internal actual fun decodeBase64(base64: String): ByteArray {
    return Base64.decode(base64, Base64.DEFAULT)
}

