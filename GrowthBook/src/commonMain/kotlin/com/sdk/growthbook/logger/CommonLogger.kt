package com.sdk.growthbook.logger

class CommonLogger: GBLogger {

    override fun log(message: String) {
        commonPrintln("log: $message")
    }

    override fun warning(warnMessage: String) {
        commonPrintln("warning: $warnMessage")
    }

    private fun commonPrintln(string: String) {
        println("$LOG_TAG $string")
    }

    companion object {
        internal const val LOG_TAG = "GrowthBook"
    }
}
