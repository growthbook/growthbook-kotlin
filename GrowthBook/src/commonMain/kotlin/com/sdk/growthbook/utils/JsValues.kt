package com.sdk.growthbook.utils

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import kotlin.math.abs

/*
 * JS coercions used by condition equality, so `{"n": "10"}` or `{"n": 0}` match the same
 * attributes they match in the JS SDK. A missing attribute is null, as JS getPath returns.
 */

private const val MAX_SAFE_INTEGER = 9007199254740992.0

private val JS_DECIMAL = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

private const val JS_WHITESPACE =
    " \t\n\u000B\u000C\r            " +
        "     　﻿"

/** JS null: a missing attribute is null, as JS getPath returns. */
internal fun GBValue?.isJsNull(): Boolean = this == null || this is GBNull || this is GBValue.Unknown

/** The number as JS sees it: a Float is taken at its decimal value (0.1f is 0.1). */
internal fun GBNumber.toJsDouble(): Double =
    if (value is Float) value.toString().toDouble() else value.toDouble()

/** JS `String(n)`: `10`, `1.5`, `1e+21`, `1e-7`. */
internal fun GBNumber.toJsString(): String {
    val isInteger = value is Byte || value is Short || value is Int || value is Long
    if (isInteger && abs(value.toDouble()) < MAX_SAFE_INTEGER) return value.toLong().toString()
    return jsNumberToString(toJsDouble())
}

internal fun jsNumberToString(number: Double): String {
    if (number.isNaN()) return "NaN"
    if (number.isInfinite()) return if (number > 0) "Infinity" else "-Infinity"
    if (number == 0.0) return "0"
    if (number < 0) return "-" + jsNumberToString(-number)

    // Kotlin prints the same shortest digits as JS, in its own layout ("1.0E21", "1e+21", "0.001").
    val text = number.toString()
    val mantissa = text.substringBefore('e').substringBefore('E')
    val exponent = if (mantissa.length < text.length) {
        text.substring(mantissa.length + 1).removePrefix("+").toInt()
    } else {
        0
    }
    val integerPart = mantissa.substringBefore('.')
    var digits = integerPart + mantissa.substringAfter('.', "")
    var pointPosition = integerPart.length + exponent
    while (digits.length > 1 && digits[0] == '0') {
        digits = digits.substring(1)
        pointPosition--
    }
    digits = digits.trimEnd('0')

    val k = digits.length
    val n = pointPosition
    return when {
        n in k..21 -> digits + "0".repeat(n - k)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            val head = if (k == 1) digits else digits[0] + "." + digits.substring(1)
            head + "e" + (if (e >= 0) "+" else "-") + abs(e)
        }
    }
}

/** JS `String(value)` (also `value + ""`). */
internal fun GBValue?.toJsString(): String = when (this) {
    null, is GBNull, is GBValue.Unknown -> "null"
    is GBBoolean -> value.toString()
    is GBString -> value
    is GBNumber -> toJsString()
    is GBArray -> joinToString(",") { if (it.isJsNull()) "" else it.toJsString() }
    is GBJson -> "[object Object]"
}

/** JS `Number(value)` (also `value * 1`). */
internal fun GBValue?.toJsNumber(): Double = when (this) {
    null, is GBNull, is GBValue.Unknown -> 0.0
    is GBBoolean -> if (value) 1.0 else 0.0
    is GBNumber -> toJsDouble()
    is GBString -> jsStringToNumber(value)
    is GBArray -> jsStringToNumber(toJsString())
    is GBJson -> Double.NaN
}

private fun jsStringToNumber(text: String): Double {
    val trimmed = text.trim { it in JS_WHITESPACE }
    if (trimmed.isEmpty()) return 0.0
    when (trimmed) {
        "Infinity", "+Infinity" -> return Double.POSITIVE_INFINITY
        "-Infinity" -> return Double.NEGATIVE_INFINITY
    }
    if (trimmed.length > 2 && trimmed[0] == '0') {
        val radix = when (trimmed[1]) {
            'x', 'X' -> 16
            'o', 'O' -> 8
            'b', 'B' -> 2
            else -> 0
        }
        if (radix != 0) {
            var result = 0.0
            for (char in trimmed.substring(2)) {
                val digit = char.digitToIntOrNull(radix) ?: return Double.NaN
                result = result * radix + digit
            }
            return result
        }
    }
    return if (JS_DECIMAL.matches(trimmed)) trimmed.toDouble() else Double.NaN
}

/** JS `!!value`. */
internal fun GBValue?.isJsTruthy(): Boolean = when (this) {
    null, is GBNull, is GBValue.Unknown -> false
    is GBBoolean -> value
    is GBNumber -> toJsDouble().let { it != 0.0 && !it.isNaN() }
    is GBString -> value.isNotEmpty()
    is GBArray, is GBJson -> true
}

/** JS `JSON.stringify(value)`. */
internal fun GBValue?.toJsJson(): String = StringBuilder().also { it.appendJsJson(this) }.toString()

private fun StringBuilder.appendJsJson(value: GBValue?) {
    when (value) {
        null, is GBNull, is GBValue.Unknown -> append("null")
        is GBBoolean -> append(value.value)
        is GBNumber -> append(if (value.toJsDouble().isFinite()) value.toJsString() else "null")
        is GBString -> appendJsonString(value.value)
        is GBArray -> {
            append('[')
            value.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendJsJson(item)
            }
            append(']')
        }
        is GBJson -> {
            append('{')
            jsKeyOrder(value.keys).forEachIndexed { index, key ->
                if (index > 0) append(',')
                appendJsonString(key)
                append(':')
                appendJsJson(value[key])
            }
            append('}')
        }
    }
}

/** JS objects list integer-like keys first, in ascending order, then the rest in insertion order. */
private fun jsKeyOrder(keys: Set<String>): List<String> {
    val (indices, names) = keys.partition { key ->
        key == "0" || (key.isNotEmpty() && key[0] in '1'..'9' && key.length <= 10 &&
            key.all { it in '0'..'9' } && key.toLong() < 4294967295L)
    }
    return indices.sortedBy { it.toLong() } + names
}

private fun StringBuilder.appendJsonString(text: String) {
    append('"')
    for ((index, char) in text.withIndex()) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\b' -> append("\\b")
            char == '\u000C' -> append("\\f")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' || isLoneSurrogate(text, index) ->
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private fun isLoneSurrogate(text: String, index: Int): Boolean {
    val char = text[index]
    return when {
        char.isHighSurrogate() -> index + 1 >= text.length || !text[index + 1].isLowSurrogate()
        char.isLowSurrogate() -> index == 0 || !text[index - 1].isHighSurrogate()
        else -> false
    }
}
