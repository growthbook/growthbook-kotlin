package com.sdk.growthbook.model

data object GBNull: GBValue()
data class GBBoolean(val value: Boolean): GBValue()
data class GBString(val value: String): GBValue()
data class GBNumber(val value: Number): GBValue()
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

    companion object {
        internal fun from(anyObj: Any): GBValue =
            when(anyObj) {
                is Boolean -> GBBoolean(anyObj)
                is String -> GBString(anyObj)
                is Number -> GBNumber(anyObj)
                else -> Unknown
            }
    }
}
