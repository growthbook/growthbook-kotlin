package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils.Companion.toHashValue
import com.sdk.growthbook.utils.jsNumberToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Number parsing, number formatting and attribute paths against the JS SDK.
 * Every expected value was produced by the JS SDK (by Guy Langford-Lee, from growthbook-kotlin#275).
 */
class JsNumberTests {

    private val evaluator = GBConditionEvaluator()

    private fun json(text: String) = GBValue.from(Json.parseToJsonElement(text))

    private fun eval(condition: String, attributes: Map<String, GBValue>) =
        evaluator.evalCondition(attributes, json(condition) as GBJson, null)

    @Test
    fun integerAndDecimalNumbersAreEqualByValue() {
        assertEquals(GBNumber(1), GBNumber(1.0))
        assertEquals(GBNumber(1).hashCode(), GBNumber(1.0).hashCode())
        assertEquals(GBNumber(0.1f), GBNumber(0.1))
        assertTrue(eval("""{"n":1}""", mapOf("n" to GBNumber(1.0))))
        assertTrue(eval("""{"n":{"${'$'}in":[1,2]}}""", mapOf("n" to GBNumber(1.0))))
        assertTrue(eval("""{"n":{"${'$'}gte":0.1}}""", mapOf("n" to GBNumber(0.1))))
    }

    @Test
    fun decimalsParseAsDouble() {
        assertEquals(0.1, (json("0.1") as GBNumber).value)
        assertEquals(123456789.123, (json("123456789.123") as GBNumber).value)
    }

    @Test
    fun numericHashAttributesFormatLikeJs() {
        assertEquals("123456789.123", json("123456789.123").toHashValue())
        assertEquals("1", GBNumber(1.0).toHashValue())
        assertEquals("1.5", json("1.5").toHashValue())
        assertEquals("1e+21", json("1e21").toHashValue())
        assertEquals("100", json("100").toHashValue())
        assertEquals("9007199254740994", GBNumber(9007199254740994L).toHashValue())
    }

    @Test
    fun numbersFormatLikeJsString() {
        assertEquals("0.30000000000000004", jsNumberToString(0.1 + 0.2))
        assertEquals("1e-7", jsNumberToString(1e-7))
        assertEquals("-0.000001", jsNumberToString(-0.000001))
        assertEquals("100000000000000000000", jsNumberToString(1e20))
        assertEquals("1.5e+300", jsNumberToString(1.5e300))
        assertEquals("0", jsNumberToString(-0.0))
    }

    @Test
    fun pathsIndexArraysAndEndAtNonObjects() {
        val attributes = (json("""{"a":["x","y"],"s":"text","o":{"b":1}}""") as GBJson).toMap()
        assertEquals(GBString("y"), evaluator.getPath(attributes, "a.1"))
        assertEquals(GBNumber(2), evaluator.getPath(attributes, "a.length"))
        assertEquals(GBNull, evaluator.getPath(attributes, "a.2"))
        assertEquals(GBNull, evaluator.getPath(attributes, "a.01"))
        assertEquals(GBNull, evaluator.getPath(attributes, "s.length"))
        assertEquals(GBNull, evaluator.getPath(attributes, "o.b.c"))
        assertTrue(eval("""{"a.0":"x"}""", attributes))
    }
}
