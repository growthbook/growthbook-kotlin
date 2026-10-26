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

/**
 * JS `String(n)` (ECMAScript Number::toString): the shortest digits that parse back to [number],
 * laid out as `10`, `1.5`, `1e+21` or `1e-7`.
 */
internal fun jsNumberToString(number: Double): String {
    if (number.isNaN()) return "NaN"
    if (number.isInfinite()) return if (number > 0) "Infinity" else "-Infinity"
    if (number == 0.0) return "0"
    if (number < 0) return "-" + jsNumberToString(-number)

    val (digits, n) = shortestDigits(number)
    val k = digits.length
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

/**
 * The shortest digits `d1..dk` that parse back to a positive finite [number], closest to it (even
 * on a tie), and the exponent `n` with `number ≈ 0.d1..dk × 10^n`. `Double.toString` isn't used:
 * the JVM before JDK 19 doesn't always print the shortest digits (`1e23` is `9.999999999999999E22`).
 */
private fun shortestDigits(number: Double): Pair<String, Int> {
    val (exact, n) = exactDigits(number)
    for (length in 1 until exact.length) {
        // The two candidates of this length either side of the exact value, closer one first
        val down = exact.substring(0, length)
        val up = incremented(down)
        val rest = exact.substring(length)
        val roundUp = rest[0] > '5' || rest[0] == '5' && (rest.length > 1 || (down.last() - '0') % 2 == 1)
        val down1 = down to n
        // Incrementing 99 gives 100, one digit longer, so the exponent moves up
        val up1 = if (up.length > length) up.trimEnd('0') to n + 1 else up to n
        for ((digits, exponent) in if (roundUp) listOf(up1, down1) else listOf(down1, up1)) {
            if ("${digits}e${exponent - digits.length}".toDouble() == number) return digits to exponent
        }
    }
    return exact to n
}

/** The decimal digits of [digits] plus one in its last place. */
private fun incremented(digits: String): String {
    val chars = digits.toCharArray()
    var i = chars.size - 1
    while (i >= 0 && chars[i] == '9') chars[i--] = '0'
    return if (i < 0) "1" + chars.concatToString() else chars.also { it[i]++ }.concatToString()
}

/**
 * All the decimal digits of a positive finite [number], without trailing zeros, and the exponent
 * `n` with `number = 0.d1..dk × 10^n` exactly.
 */
private fun exactDigits(number: Double): Pair<String, Int> {
    val bits = number.toRawBits()
    val biasedExponent = ((bits ushr 52) and 0x7FF).toInt()
    val fraction = bits and 0xFFFFFFFFFFFFFL
    val mantissa = if (biasedExponent == 0) fraction else fraction or (1L shl 52)
    val exponent = maxOf(biasedExponent, 1) - 1075

    // number = mantissa × 2^exponent, or mantissa × 5^-exponent / 10^-exponent when it's negative.
    // Digits are least significant first.
    val digits = mantissa.toString().reversed().mapTo(ArrayList()) { it - '0' }
    val factor = if (exponent >= 0) 2L else 5L
    // 2^30 and 5^13 keep each digit's product within a Long
    val maxPower = if (exponent >= 0) 30 else 13
    var remaining = abs(exponent)
    while (remaining > 0) {
        val power = minOf(remaining, maxPower)
        var multiplier = 1L
        repeat(power) { multiplier *= factor }
        var carry = 0L
        for (i in digits.indices) {
            val product = digits[i] * multiplier + carry
            digits[i] = (product % 10).toInt()
            carry = product / 10
        }
        while (carry > 0) {
            digits.add((carry % 10).toInt())
            carry /= 10
        }
        remaining -= power
    }

    val text = StringBuilder(digits.size).apply { for (i in digits.indices.reversed()) append('0' + digits[i]) }
    val n = if (exponent >= 0) text.length else text.length + exponent
    return text.toString().trimEnd('0') to n
}
