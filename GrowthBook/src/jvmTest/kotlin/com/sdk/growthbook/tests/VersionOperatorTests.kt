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
 * The `$v*` family against operands that are not strings.
 *
 * Both operands used to be cast to `GBString`, so a number fell back to a placeholder: the
 * attribute became version `"0"` and the condition became the empty string. A payload that sent a
 * build number as a JSON number — the natural shape for an Android `versionCode` — therefore
 * matched no version rule at all, with no error raised anywhere. The reference SDK coerces instead
 * (`util.ts`: `if (typeof input === "number") input = input + ""`).
 *
 * What made this hard to spot is that the placeholder is still a valid version, so roughly half
 * the comparisons came out right by accident: `10 $vlt "99"` compared `"0"` against `"99"` and
 * answered true, which is also the correct answer. The suite therefore asserts both directions of
 * each comparison, where a placeholder cannot satisfy both.
 *
 * The shared spec fixtures (`cases.json`, spec 0.8.0) only ever pass version strings.
 */
class VersionOperatorTests {

    @Test
    fun testStringOperandsOnBothSides() {
        assertTenIsGreaterThanNine(""""10"""", """"9"""")
        assertVersionsEqual(""""10"""", """"10"""")
    }

    @Test
    fun testSemverStringsStillCompare() {
        assertTrue(eval("""{"v": {"${'$'}vgt": "1.0.0"}}""", """{"v": "1.2.0"}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1.2.10"}}""", """{"v": "1.2.9"}"""))
        assertTrue(eval("""{"v": {"${'$'}veq": "1.2.3"}}""", """{"v": "v1.2.3+build99"}"""))
        assertTrue(eval("""{"v": {"${'$'}vgt": "1.0.0-beta"}}""", """{"v": "1.0.0"}"""))
    }

    @Test
    fun testNumericOnBothSides() {
        assertTenIsGreaterThanNine("10", "9")
        assertVersionsEqual("10", "10")
    }

    @Test
    fun testEmptyStringIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": ""}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": ""}"""))
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

    /** Asserts the whole operator family at once for a `10` vs `9` pair, however each is encoded. */
    private fun assertTenIsGreaterThanNine(
        @Language("json") ten: String,
        @Language("json") nine: String,
    ) {
        val attrs = """{"v": $ten}"""
        assertTrue(eval("""{"v": {"${'$'}vgt": $nine}}""", attrs), "\$vgt")
        assertTrue(eval("""{"v": {"${'$'}vgte": $nine}}""", attrs), "\$vgte")
        assertTrue(eval("""{"v": {"${'$'}vne": $nine}}""", attrs), "\$vne")
        assertFalse(eval("""{"v": {"${'$'}vlt": $nine}}""", attrs), "\$vlt")
        assertFalse(eval("""{"v": {"${'$'}vlte": $nine}}""", attrs), "\$vlte")
        assertFalse(eval("""{"v": {"${'$'}veq": $nine}}""", attrs), "\$veq")
    }

    /** Asserts the whole family for a pair that is equal, however each side is encoded. */
    private fun assertVersionsEqual(
        @Language("json") left: String,
        @Language("json") right: String,
    ) {
        val attrs = """{"v": $left}"""
        assertTrue(eval("""{"v": {"${'$'}veq": $right}}""", attrs), "\$veq")
        assertTrue(eval("""{"v": {"${'$'}vgte": $right}}""", attrs), "\$vgte")
        assertTrue(eval("""{"v": {"${'$'}vlte": $right}}""", attrs), "\$vlte")
        assertFalse(eval("""{"v": {"${'$'}vne": $right}}""", attrs), "\$vne")
        assertFalse(eval("""{"v": {"${'$'}vgt": $right}}""", attrs), "\$vgt")
        assertFalse(eval("""{"v": {"${'$'}vlt": $right}}""", attrs), "\$vlt")
    }
}
