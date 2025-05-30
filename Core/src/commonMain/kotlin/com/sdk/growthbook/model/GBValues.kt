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
