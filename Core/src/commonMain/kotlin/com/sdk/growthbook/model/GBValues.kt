package com.sdk.growthbook.model

data object GBNull: GBValue()
data class GBBoolean(val value: Boolean): GBValue()
data class GBString(val value: String): GBValue()
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

data class GBNumber(val value: Number): GBValue() {
    override fun equals(other: Any?): Boolean {
        if (other is GBNumber) {
            return JsNumber(value) == JsNumber(other.value)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
