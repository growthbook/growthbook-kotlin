package com.sdk.growthbook.utils

import com.sdk.growthbook.model.GBNumber
import kotlin.math.abs

/*
 * JS number formatting, so a numeric hash attribute hashes to the same bucket as in the JS
 * SDK: `String(1.0)` is "1" and `String(1e21)` is "1e+21".
 */

private const val MAX_SAFE_INTEGER = 9007199254740992.0

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
