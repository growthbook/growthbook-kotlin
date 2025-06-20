package com.sdk.growthbook.logger

internal interface GBLogger {
    fun log(message: String)
    fun warning(warnMessage: String)
}

internal expect fun getLogger(): GBLogger

internal val GB: GBLogger = getLogger()
