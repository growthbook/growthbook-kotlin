package com.sdk.growthbook.model

import kotlin.math.abs

private const val MAX_SAFE_INTEGER = 9007199254740992.0

data object GBNull: GBValue()
data class GBBoolean(val value: Boolean): GBValue()
data class GBString(val value: String): GBValue()

class GBNumber(val value: Number): GBValue() {

    /** Numbers are equal by value, as in JS: 1 == 1.0. */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is GBNumber) {
            return false
        }
        if (isIntegerValue() && other.isIntegerValue()) {
            return value.toLong() == other.value.toLong()
        }
        return doubleValue() == other.doubleValue()
    }

    override fun hashCode(): Int {
        val number = doubleValue()
        return if (number % 1.0 == 0.0 && abs(number) < MAX_SAFE_INTEGER) {
            number.toLong().hashCode()
        } else {
            number.hashCode()
        }
    }

    override fun toString(): String = "GBNumber(value=$value)"

    private fun isIntegerValue(): Boolean =
        value is Byte || value is Short || value is Int || value is Long

    // A Float is taken at its decimal value (0.1f is 0.1), as it was before decimals became Double.
    private fun doubleValue(): Double =
        if (value is Float) value.toString().toDouble() else value.toDouble()

}

data class GBArray(
    private val value: List<GBValue>,
) : GBValue(), List<GBValue> by value {

    /**
     * Membership index for large `$in` / `$nin` arrays.
     * Keeps the original list for order/serialize; Set is an O(1) lookup side structure.
     * Built lazily on first [contains] for arrays at/above [MEMBERSHIP_SET_THRESHOLD]
     * so small lists stay list-only (no extra memory or Set overhead).
     */
    private val membershipSet: Set<GBValue> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        value.toHashSet()
    }

    override fun contains(element: GBValue): Boolean =
        if (value.size < MEMBERSHIP_SET_THRESHOLD) {
            value.contains(element)
        } else {
            membershipSet.contains(element)
        }

    private companion object {
        /** Below this size, linear scan beats HashSet lookup for GBValue equality. */
        const val MEMBERSHIP_SET_THRESHOLD = 16
    }
}
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
