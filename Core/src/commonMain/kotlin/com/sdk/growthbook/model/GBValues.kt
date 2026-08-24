package com.sdk.growthbook.model

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
            value.toDouble() == other.value.toDouble()
        ) {
            return true
        }
        return false
    }

    override fun hashCode(): Int =
        if (isIntegerValue()) {
            value.toLong().hashCode()
        } else {
            value.toDouble().hashCode()
        }

    override fun toString(): String = "GBNumber(value=$value)"

    private fun isIntegerValue(): Boolean =
        value is Byte || value is Short || value is Int || value is Long

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
