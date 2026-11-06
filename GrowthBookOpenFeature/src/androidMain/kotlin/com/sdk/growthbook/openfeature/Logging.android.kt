package com.sdk.growthbook.openfeature

import android.util.Log

internal actual fun logError(message: String, throwable: Throwable) {
    Log.e("GrowthBook", message, throwable)
}
