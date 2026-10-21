package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils.Companion.toHashValue
import com.sdk.growthbook.utils.jsNumberToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plain (non-operator) condition equality, number parsing and number formatting against the
 * JS SDK. Every expected value was produced by the JS SDK's evalCondition / String().
 */
class JsEqualityTests {

    private class JsCase(val name: String, val condition: String, val attributes: String, val expected: Boolean)

    private val evaluator = GBConditionEvaluator()

    private fun json(text: String) = GBValue.from(Json.parseToJsonElement(text))

    private fun eval(condition: String, attributes: Map<String, GBValue>) =
        evaluator.evalCondition(attributes, json(condition) as GBJson, null)

    private val cases = listOf(
        JsCase("string cond vs number attr", """{"n":"10"}""", """{"n":10}""", true),
        JsCase("string cond vs decimal attr", """{"n":"1.5"}""", """{"n":1.5}""", true),
        JsCase("string cond vs 1e21 attr", """{"n":"1e+21"}""", """{"n":1e21}""", true),
        JsCase("string cond vs bool attr", """{"n":"true"}""", """{"n":true}""", true),
        JsCase("string cond vs null attr", """{"n":"null"}""", """{"n":null}""", true),
        JsCase("string cond vs missing attr", """{"n":"null"}""", """{}""", true),
        JsCase("string cond undefined vs missing attr", """{"n":"undefined"}""", """{}""", false),
        JsCase("string cond vs array attr", """{"n":"a,b"}""", """{"n":["a","b"]}""", true),
        JsCase("string cond vs array with null", """{"n":"a,,1"}""", """{"n":["a",null,1]}""", true),
        JsCase("string cond vs object attr", """{"n":"[object Object]"}""", """{"n":{"x":1}}""", true),
        JsCase("string cond vs same string", """{"n":"10"}""", """{"n":"10"}""", true),
        JsCase("string cond vs other string", """{"n":"10"}""", """{"n":"010"}""", false),
        JsCase("number cond vs numeric string", """{"n":5}""", """{"n":"5"}""", true),
        JsCase("number cond vs padded numeric string", """{"n":5}""", """{"n":" 5 "}""", true),
        JsCase("number cond vs hex string", """{"n":16}""", """{"n":"0x10"}""", true),
        JsCase("number cond vs decimal string", """{"n":1.5}""", """{"n":"1.5"}""", true),
        JsCase("number cond vs non-numeric string", """{"n":5}""", """{"n":"abc"}""", false),
        JsCase("number 0 cond vs empty string", """{"n":0}""", """{"n":""}""", true),
        JsCase("number 1 cond vs true", """{"n":1}""", """{"n":true}""", true),
        JsCase("number 0 cond vs false", """{"n":0}""", """{"n":false}""", true),
        JsCase("number 0 cond vs null", """{"n":0}""", """{"n":null}""", true),
        JsCase("number 0 cond vs missing", """{"n":0}""", """{}""", true),
        JsCase("number cond vs single-item array", """{"n":5}""", """{"n":[5]}""", true),
        JsCase("number cond vs object", """{"n":5}""", """{"n":{"a":5}}""", false),
        JsCase("number cond vs equal number", """{"n":9.99}""", """{"n":9.99}""", true),
        JsCase("bool true cond vs string false", """{"n":true}""", """{"n":"false"}""", true),
        JsCase("bool true cond vs string 0", """{"n":true}""", """{"n":"0"}""", true),
        JsCase("bool false cond vs number 0", """{"n":false}""", """{"n":0}""", true),
        JsCase("bool false cond vs empty string", """{"n":false}""", """{"n":""}""", true),
        JsCase("bool false cond vs missing", """{"n":false}""", """{}""", false),
        JsCase("bool true cond vs missing", """{"n":true}""", """{}""", false),
        JsCase("bool false cond vs null", """{"n":false}""", """{"n":null}""", false),
        JsCase("bool true cond vs empty array", """{"n":true}""", """{"n":[]}""", true),
        JsCase("bool true cond vs empty object", """{"n":true}""", """{"n":{}}""", true),
        JsCase("bool true cond vs number 2", """{"n":true}""", """{"n":2}""", true),
        JsCase("null cond vs missing", """{"n":null}""", """{}""", true),
        JsCase("null cond vs null", """{"n":null}""", """{"n":null}""", true),
        JsCase("null cond vs 0", """{"n":null}""", """{"n":0}""", false),
        JsCase("null cond vs empty string", """{"n":null}""", """{"n":""}""", false),
        JsCase("array cond vs equal array", """{"n":[1,"a"]}""", """{"n":[1,"a"]}""", true),
        JsCase("array cond vs reordered array", """{"n":[1,2]}""", """{"n":[2,1]}""", false),
        JsCase("array cond vs string", """{"n":["a"]}""", """{"n":"a"}""", false),
        JsCase("object cond key order matters", """{"n":{"a":1,"b":2}}""", """{"n":{"b":2,"a":1}}""", false),
        JsCase("object cond equal", """{"n":{"a":1,"b":[2]}}""", """{"n":{"a":1,"b":[2]}}""", true),
        JsCase("object cond integer keys sort first", """{"n":{"1":1,"b":2}}""", """{"n":{"b":2,"1":1}}""", true),
        JsCase("object cond vs missing", """{"n":{"a":1}}""", """{}""", false),
        JsCase("${'$'}size coerces string condition", """{"n":{"${'$'}size":"2"}}""", """{"n":[1,2]}""", true),
        JsCase("${'$'}all coerces items", """{"n":{"${'$'}all":[1]}}""", """{"n":["1"]}""", true),
        JsCase("${'$'}not with number cond vs numeric string", """{"n":{"${'$'}not":5}}""", """{"n":"5"}""", false),
        JsCase("dot path numeric index into array", """{"a.0":"x"}""", """{"a":["x","y"]}""", true),
        JsCase("dot path index out of range is null", """{"a.5":null}""", """{"a":["x"]}""", true),
        JsCase("dot path array length", """{"a.length":2}""", """{"a":["x","y"]}""", true),
        JsCase("dot path key into array of objects does not fan out", """{"items.name":"x"}""", """{"items":[{"name":"x"}]}""", false),
        JsCase("dot path through string is null", """{"a.b":null}""", """{"a":"x"}""", true),
        JsCase("dot path through string does not match string", """{"a.b":"x"}""", """{"a":"x"}""", false),
    )

    @Test
    fun plainEqualityMatchesJs() {
        val failures = cases.filter { case ->
            eval(case.condition, (json(case.attributes) as GBJson).toMap()) != case.expected
        }.map { it.name }
        assertEquals(emptyList(), failures)
    }

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
}
