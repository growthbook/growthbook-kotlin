package com.sdk.growthbook.model

internal data class JsNumber(val value: Number) { // Javascript number
    override fun equals(other: Any?): Boolean {
        return if (other is JsNumber) {

            if (value is Int && other.value is Long) {
                return value.toLong() == other.value
            }

            value == other.value
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
