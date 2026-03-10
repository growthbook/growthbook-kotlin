package com.sdk.growthbook.logger

internal interface GBLogger {
    fun log(message: String)
    fun warning(warnMessage: String)
    fun error(errorMessage: String, throwable: Throwable?)
}

internal expect fun getLogger(): GBLogger

internal val GB: GBLogger = getLogger()
