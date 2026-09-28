package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `$lt` / `$lte` / `$gt` / `$gte` against operands that are not both numbers.
 *
 * The reference SDK compares with JavaScript's relational operators: two strings compare
 * lexicographically, anything else is converted with `Number()` first. A value with no numeric
 * reading becomes `NaN`, and every comparison with `NaN` is false.
 *
 * This SDK used to turn such a value into `0` instead — `"abc"` was less than `1` — and read a
 * boolean as `0` either way, so `true` was not greater than `0`. Kotlin's `toDoubleOrNull` also
 * differs from `Number()` at the edges: it accepts `"1f"` and rejects `"0x10"`.
 *
 * The shared spec fixtures only compare numbers with numbers and strings with strings. Hence
 * this suite.
 */
class RangeOperatorTests {

    @Test
    fun testNumbersAndStringsAreUnchanged() {
        assertTrue(eval("""{"v": {"${'$'}gt": 9}}""", """{"v": 10}"""))
        assertTrue(eval("""{"v": {"${'$'}lt": "banana"}}""", """{"v": "apple"}"""))
        // Two strings compare as text, not as numbers
        assertTrue(eval("""{"v": {"${'$'}lt": "9"}}""", """{"v": "10"}"""))
    }

    /** A numeric string against a number is compared as a number, in every direction. */
    @Test
    fun testANumericStringComparesAsANumberAgainstANumber() {
        assertTrue(eval("""{"v": {"${'$'}gt": 9}}""", """{"v": "10"}"""))
        assertTrue(eval("""{"v": {"${'$'}gte": 10}}""", """{"v": "10"}"""))
        assertTrue(eval("""{"v": {"${'$'}lte": "10"}}""", """{"v": 3}"""))
        assertTrue(eval("""{"v": {"${'$'}lt": 10}}""", """{"v": " 5 "}"""))
    }

    /** `Number(true)` is 1 and `Number(false)` is 0. */
    @Test
    fun testABooleanComparesAsOneOrZero() {
        assertTrue(eval("""{"v": {"${'$'}gt": 0}}""", """{"v": true}"""))
        assertTrue(eval("""{"v": {"${'$'}lt": 1}}""", """{"v": false}"""))
        assertFalse(eval("""{"v": {"${'$'}gt": 1}}""", """{"v": true}"""))
    }

    /** A value with no numeric reading is `NaN`, so no direction holds — it used to be 0. */
    @Test
    fun testANonNumericStringMatchesNoDirection() {
        for (op in listOf("lt", "lte", "gt", "gte")) {
            assertFalse(eval("""{"v": {"${'$'}$op": 1}}""", """{"v": "abc"}"""), "\$$op, \"abc\"")
            assertFalse(eval("""{"v": {"${'$'}$op": "abc"}}""", """{"v": 1}"""), "\$$op vs \"abc\"")
        }
    }

    /** A type suffix is not part of a JavaScript number. */
    @Test
    fun testATypeSuffixIsNotANumber() {
        assertFalse(eval("""{"v": {"${'$'}lt": 2}}""", """{"v": "1f"}"""))
        assertFalse(eval("""{"v": {"${'$'}lt": 2}}""", """{"v": "1d"}"""))
    }

    /** `Number()` reads hexadecimal, octal and binary literals. */
    @Test
    fun testNonDecimalLiteralsAreRead() {
        assertTrue(eval("""{"v": {"${'$'}lt": 20}}""", """{"v": "0x10"}"""))
        assertTrue(eval("""{"v": {"${'$'}gt": 15}}""", """{"v": "0x10"}"""))
        assertTrue(eval("""{"v": {"${'$'}gte": 8}}""", """{"v": "0o10"}"""))
        assertTrue(eval("""{"v": {"${'$'}lte": 2}}""", """{"v": "0b10"}"""))
        assertFalse(eval("""{"v": {"${'$'}lt": 20}}""", """{"v": "0xZZ"}"""))
    }

    /** `Number("")` and `Number(null)` are 0. */
    @Test
    fun testAnEmptyStringAndNullCompareAsZero() {
        assertTrue(eval("""{"v": {"${'$'}lt": 1}}""", """{"v": ""}"""))
        assertTrue(eval("""{"v": {"${'$'}lt": 1}}""", """{"v": null}"""))
        assertTrue(eval("""{"v": {"${'$'}lt": "5"}}""", """{"v": null}"""))
    }

    private fun eval(
        @Language("json") condition: String,
        @Language("json") attributes: String
    ): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            null
        )
}
