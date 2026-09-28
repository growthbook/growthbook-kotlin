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
    fun testNumericAttributeAgainstStringCondition() {
        assertTenIsGreaterThanNine("10", """"9"""")
    }

    @Test
    fun testNumericAttributeEqualsStringCondition() {
        assertVersionsEqual("10", """"10"""")
    }

    @Test
    fun testStringAttributeAgainstNumericCondition() {
        assertTenIsGreaterThanNine(""""10"""", "9")
    }

    @Test
    fun testStringAttributeEqualsNumericCondition() {
        assertVersionsEqual(""""10"""", "10")
    }

    @Test
    fun testNumericOnBothSides() {
        assertTenIsGreaterThanNine("10", "9")
        assertVersionsEqual("10", "10")
    }

    @Test
    fun testLargeBuildNumbers() {
        assertTenIsGreaterThanNine("45210", "9999")
        assertVersionsEqual("45210", """"45210"""")
    }

    /**
     * An integral operand must not be normalised through a double on its way to a string: past
     * 2^53 that loses digits (`1234567890123456789` becomes `1234567890123456768`), which would
     * compare a version the payload never sent.
     */
    @Test
    fun testLargeIntegerOperandKeepsEveryDigit() {
        assertVersionsEqual("1234567890123456789", """"1234567890123456789"""")
        assertTrue(
            eval(
                """{"v": {"${'$'}vne": "1234567890123456768"}}""",
                """{"v": 1234567890123456789}"""
            )
        )
    }

    /**
     * An integral value must not carry a fractional part into the comparison: `10.0` would split
     * into two version segments and stop matching `"10"`, while the reference SDK renders it `10`.
     */
    @Test
    fun testIntegralDecimalRendersWithoutFraction() {
        assertVersionsEqual("10.0", """"10"""")
        assertTenIsGreaterThanNine("10.0", """"9"""")
    }

    @Test
    fun testNonIntegralDecimalKeepsItsFraction() {
        assertVersionsEqual("1.5", """"1.5"""")
        assertTrue(eval("""{"v": {"${'$'}vgt": "1.4"}}""", """{"v": 1.5}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1.6"}}""", """{"v": 1.5}"""))
    }

    @Test
    fun testZeroIsAVersionNotAPlaceholder() {
        assertVersionsEqual("0", """"0"""")
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": 0}"""))
    }

    /**
     * Absent, null, boolean and empty-string operands all fall back to version `"0"`, as in the
     * reference SDK. Asserting both directions keeps that a deliberate value rather than something
     * that merely happens to satisfy one comparison.
     */
    @Test
    fun testAbsentAttributeIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"other": "x"}"""))
        assertFalse(eval("""{"v": {"${'$'}vgt": "1"}}""", """{"other": "x"}"""))
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"other": "x"}"""))
    }

    @Test
    fun testNullAttributeIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": null}"""))
        assertFalse(eval("""{"v": {"${'$'}vgt": "1"}}""", """{"v": null}"""))
    }

    @Test
    fun testBooleanAttributeIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": true}"""))
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": false}"""))
    }

    @Test
    fun testEmptyStringIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": ""}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": ""}"""))
    }

    /**
     * Rendering the operand is only half the job — the operator has to be reached at all. Array and
     * object attributes are routed to the `$elemMatch` / `$size` branch, so confining the version
     * operators to primitive attributes made them skip the comparison entirely and answer false
     * both ways, rather than comparing as version `"0"`.
     */
    @Test
    fun testArrayAttributeIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": ["1.0.0"]}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": ["1.0.0"]}"""))
        assertFalse(eval("""{"v": {"${'$'}vgt": "1"}}""", """{"v": ["1.0.0"]}"""))
    }

    @Test
    fun testObjectAttributeIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}veq": "0"}}""", """{"v": {"major": 1}}"""))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1"}}""", """{"v": {"major": 1}}"""))
        assertFalse(eval("""{"v": {"${'$'}vgt": "1"}}""", """{"v": {"major": 1}}"""))
    }

    @Test
    fun testArrayConditionIsVersionZero() {
        assertTrue(eval("""{"v": {"${'$'}vgt": ["9"]}}""", """{"v": "1.0.0"}"""))
        assertTrue(eval("""{"v": {"${'$'}veq": ["9"]}}""", """{"v": "0"}"""))
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
