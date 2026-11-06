@file:OptIn(ExperimentalTime::class)

package com.sdk.growthbook.openfeature

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import dev.openfeature.kotlin.sdk.Value
import kotlin.time.ExperimentalTime

/**
 * Strict conversions between GrowthBook values and OpenFeature values.
 *
 * Unlike the SDK's value helpers, these never coerce between types or apply truthiness.
 */
internal object ValueConversion {
    fun boolean(value: GBValue): Boolean? = (value as? GBBoolean)?.value

    fun string(value: GBValue): String? = (value as? GBString)?.value

    /** Whole numbers within `Int` range only; fractions and out-of-range values are rejected. */
    fun int(value: GBValue): Int? = long(value)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** Whole numbers within `Long` range only; fractions and out-of-range values are rejected. */
    fun long(value: GBValue): Long? {
        val number = (value as? GBNumber)?.value ?: return null
        if (isIntegral(number)) return number.toLong()
        val double = toDouble(number)
        // Long.MAX_VALUE rounds up to 2^63 as a double, so the upper bound must be exclusive.
        if (!double.isFinite() || double % 1.0 != 0.0 || double < -9.223372036854776E18 || double >= 9.223372036854776E18) {
            return null
        }
        return double.toLong()
    }

    fun double(value: GBValue): Double? {
        val number = (value as? GBNumber)?.value ?: return null
        return toDouble(number).takeIf { it.isFinite() }
    }

    fun value(value: GBValue): Value? = when (value) {
        is GBBoolean -> Value.Boolean(value.value)
        is GBString -> Value.String(value.value)
        is GBNumber -> {
            val number = value.value
            when {
                isIntegral(number) && number.toLong() in Int.MIN_VALUE..Int.MAX_VALUE -> Value.Integer(number.toInt())
                isIntegral(number) -> Value.Long(number.toLong())
                else -> double(value)?.let { Value.Double(it) }
            }
        }
        is GBArray -> value.map { value(it) ?: return null }.let { Value.List(it) }
        is GBJson -> value.mapValues { (_, element) -> value(element) ?: return null }.let { Value.Structure(it) }
        GBNull -> Value.Null
        else -> null
    }

    /** Converts an OpenFeature context value to a GrowthBook attribute value. Instants become ISO-8601 strings. */
    fun attribute(value: Value): GBValue = when (value) {
        is Value.Boolean -> GBBoolean(value.boolean)
        is Value.String -> GBString(value.string)
        is Value.Integer -> GBNumber(value.integer)
        is Value.Long -> GBNumber(value.long)
        is Value.Double -> GBNumber(value.double)
        is Value.Instant -> GBString(value.instant.toString())
        is Value.List -> GBArray(value.list.map(::attribute))
        is Value.Structure -> GBJson(value.structure.mapValues { (_, element) -> attribute(element) })
        Value.Null -> GBNull
    }

    private fun isIntegral(number: Number): Boolean =
        number is Byte || number is Short || number is Int || number is Long

    // GrowthBook parses decimals that fit a Float as Float. Going through the decimal text keeps
    // 0.1 as 0.1 instead of 0.10000000149011612.
    private fun toDouble(number: Number): Double =
        if (number is Float) number.toString().toDouble() else number.toDouble()
}
