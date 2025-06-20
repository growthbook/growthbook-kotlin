package com.sdk.growthbook.logger

import android.util.Log

internal actual fun getLogger(): GBLogger = GBLoggerAndroid()

class GBLoggerAndroid: GBLogger {
    override fun log(message: String) {
        Log.d(TAG, message)
    }

    override fun warning(warnMessage: String) {
        Log.w(TAG, warnMessage)
    }

    companion object {
        private const val TAG = CommonLogger.Companion.LOG_TAG
    }
}
