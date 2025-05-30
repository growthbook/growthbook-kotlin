package com.sdk.growthbook.model

import kotlin.math.abs
import kotlin.math.roundToLong

data object GBNull: GBValue()
data class GBBoolean(val value: Boolean): GBValue()
data class GBString(val value: String): GBValue()

class GBNumber(val value: Number): GBValue() {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is GBNumber) {
            return false
        }
        if (isIntegerValue() && other.isIntegerValue() && value.toLong() == other.value.toLong()) {
            return true
        }
        if (
            !isIntegerValue() && !other.isIntegerValue() &&
            (
                value.toDouble() == other.value.toDouble() ||
                // Handles rare cases when attributes are computed with floating-point errors.
                abs(value.toDouble() - other.value.toDouble()) < DOUBLE_COMPARISON_EPSILON
            )
        ) {
            return true
        }
        return false
    }

    override fun hashCode(): Int =
        if (isIntegerValue()) {
            value.toLong().hashCode()
        } else {
            // Reduces precision of hash codes
            // to respect hash code equality for numbers with floating-point errors.
            (
                (value.toDouble() * DOUBLE_COMPARISON_PRECISION).roundToLong() /
                    DOUBLE_COMPARISON_PRECISION
            ).hashCode()
        }

    override fun toString(): String = "GBNumber(value=$value)"

    private fun isIntegerValue(): Boolean =
        value is Byte || value is Short || value is Int || value is Long

    private companion object {

        private const val DOUBLE_COMPARISON_PRECISION: Double = 10000000.0

        /**
         * Should be smaller than `1 / `[DOUBLE_COMPARISON_PRECISION]
         * to guarantee hash code equality for equal numbers with floating-point errors.
         */
        private const val DOUBLE_COMPARISON_EPSILON: Double = 0.9 / DOUBLE_COMPARISON_PRECISION

    }

}

class GBArray(
    value: List<GBValue>
): GBValue(), List<GBValue> by value
data class GBJson(
    private val value: Map<String, GBValue>,
): GBValue(), Map<String, GBValue> by value

fun String.toGbString() = GBString(this)
fun Number.toGbNumber() = GBNumber(this)
fun Boolean.toGbBoolean() = GBBoolean(this)

sealed class GBValue {
    data object Unknown: GBValue()

    fun isPrimitiveValue(): Boolean =
        when(this) {
            is GBNull, is GBBoolean, is GBString, is GBNumber -> true
            else -> false
        }

    companion object
}
