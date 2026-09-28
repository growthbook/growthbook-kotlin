package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `$eq` / `$ne` across every attribute type.
 *
 * `$eq` used to narrow both operands to `GBString` before comparing, so it answered false for
 * every number and boolean: `{"age": {"$eq": 25}}` did not match an age of 25, and `$eq` and `$ne`
 * both reported false for the same pair — a self-contradiction that is impossible to read as
 * anything but a bug. The reference SDK compares the decoded values directly
 * (`mongrule.ts`: `case "$eq": return actual === expected`).
 *
 * The shared spec fixtures (`cases.json`, spec 0.8.0) only ever pair `$eq` with a string, plus one
 * boolean case against a *missing* attribute that expects false — which the broken version passed
 * for the wrong reason. Hence this suite.
 *
 * Plain equality (`{"age": 25}`) goes through a different path in `evalConditionValue` and was
 * never affected; it is covered here too so the two stay in agreement.
 */
class EqualityOperatorTests {

    @Test
    fun testEqMatchesAnEqualInteger() {
        assertTrue(eval("""{"age": {"${'$'}eq": 25}}""", """{"age": 25}"""))
    }

    @Test
    fun testEqDoesNotMatchADifferentInteger() {
        assertFalse(eval("""{"age": {"${'$'}eq": 25}}""", """{"age": 30}"""))
    }

    @Test
    fun testEqMatchesAnEqualDecimal() {
        assertTrue(eval("""{"score": {"${'$'}eq": 1.5}}""", """{"score": 1.5}"""))
    }

    @Test
    fun testEqMatchesANegativeNumber() {
        assertTrue(eval("""{"delta": {"${'$'}eq": -3}}""", """{"delta": -3}"""))
    }

    @Test
    fun testNeIsTheExactInverseForNumbers() {
        assertFalse(eval("""{"age": {"${'$'}ne": 25}}""", """{"age": 25}"""))
        assertTrue(eval("""{"age": {"${'$'}ne": 25}}""", """{"age": 30}"""))
    }

    /**
     * JS has a single number type, so `25 === 25.0` in the reference SDK, and every other
     * GrowthBook SDK agrees. Targeting compares numbers by value too, so a rule written as
     * `{"age": 25}` matches an app that sets the attribute as `25.0`. Plain equality, `$eq` and
     * `$ne` must keep answering the same way.
     */
    @Test
    fun testIntegerAndIntegralDecimalAreEqualAndConsistentlySo() {
        val viaEq = eval("""{"age": {"${'$'}eq": 25}}""", """{"age": 25.0}""")
        val viaPlain = eval("""{"age": 25}""", """{"age": 25.0}""")
        val viaNe = eval("""{"age": {"${'$'}ne": 25}}""", """{"age": 25.0}""")

        assertTrue(viaEq, "\$eq compares numbers by value, as in the reference SDK")
        assertTrue(viaPlain, "plain equality must agree with \$eq")
        assertFalse(viaNe, "\$ne must stay the exact inverse of \$eq")
    }

    @Test
    fun testANonIntegralDecimalIsStillNotEqualToAnInteger() {
        assertFalse(eval("""{"age": {"${'$'}eq": 25}}""", """{"age": 25.5}"""))
        assertTrue(eval("""{"age": {"${'$'}ne": 25}}""", """{"age": 25.5}"""))
    }

    /** Plain equality compares array and object contents, so numbers inside them compare by value too. */
    @Test
    fun testPlainEqualityComparesNestedNumbersByValue() {
        assertTrue(eval("""{"scores": [1, 2]}""", """{"scores": [1.0, 2.0]}"""))
        assertTrue(eval("""{"limits": {"max": 10}}""", """{"limits": {"max": 10.0}}"""))
        assertFalse(eval("""{"scores": [1, 2]}""", """{"scores": [1.0, 2.5]}"""))
    }

    /**
     * The rule is the evaluator's, not the value model's: [GBNumber] keeps integer and
     * floating-point values distinct everywhere else in the SDK, and that is unchanged.
     */
    @Test
    fun testTheValueModelStillKeepsIntegerAndDecimalDistinct() {
        assertTrue(GBNumber(25) != GBNumber(25.0))
    }

    @Test
    fun testEqMatchesAnEqualBoolean() {
        assertTrue(eval("""{"beta": {"${'$'}eq": true}}""", """{"beta": true}"""))
        assertTrue(eval("""{"beta": {"${'$'}eq": false}}""", """{"beta": false}"""))
    }

    @Test
    fun testEqDoesNotMatchTheOppositeBoolean() {
        assertFalse(eval("""{"beta": {"${'$'}eq": true}}""", """{"beta": false}"""))
    }

    @Test
    fun testNeIsTheExactInverseForBooleans() {
        assertFalse(eval("""{"beta": {"${'$'}ne": true}}""", """{"beta": true}"""))
        assertTrue(eval("""{"beta": {"${'$'}ne": true}}""", """{"beta": false}"""))
    }

    @Test
    fun testEqMatchesAnEqualString() {
        assertTrue(eval("""{"c": {"${'$'}eq": "US"}}""", """{"c": "US"}"""))
    }

    @Test
    fun testEqIsCaseSensitive() {
        assertFalse(eval("""{"c": {"${'$'}eq": "US"}}""", """{"c": "us"}"""))
    }

    @Test
    fun testEqDoesNotMatchAcrossTypes() {
        assertFalse(eval("""{"v": {"${'$'}eq": "25"}}""", """{"v": 25}"""))
        assertFalse(eval("""{"v": {"${'$'}eq": 25}}""", """{"v": "25"}"""))
        assertFalse(eval("""{"v": {"${'$'}eq": true}}""", """{"v": "true"}"""))
    }

    @Test
    fun testEqMatchesNullAgainstAnExplicitlyNullAttribute() {
        assertTrue(eval("""{"c": {"${'$'}eq": null}}""", """{"c": null}"""))
    }

    /** `getPath` collapses a missing attribute to null, exactly as the reference SDK does. */
    @Test
    fun testEqMatchesNullAgainstAnAbsentAttribute() {
        assertTrue(eval("""{"c": {"${'$'}eq": null}}""", """{"other": "x"}"""))
    }

    @Test
    fun testEqDoesNotMatchAValueAgainstAnAbsentAttribute() {
        assertFalse(eval("""{"c": {"${'$'}eq": "US"}}""", """{"other": "x"}"""))
        assertFalse(eval("""{"c": {"${'$'}eq": false}}""", """{"other": "x"}"""))
        assertFalse(eval("""{"c": {"${'$'}eq": 0}}""", """{"other": "x"}"""))
    }

    @Test
    fun testEqAgreesWithPlainEqualityForNull() {
        assertTrue(eval("""{"c": {"${'$'}eq": null}}""", """{"other": "x"}"""))
        assertTrue(eval("""{"c": null}""", """{"other": "x"}"""))
    }

    /**
     * The reference SDK compares with `===`: value equality for primitives, reference identity for
     * arrays and objects. A condition and an attribute are always decoded from separate JSON, so a
     * non-primitive operand is never identical and `$eq` is false whatever the contents.
     *
     * That is reproduced rather than deepened into a content comparison. A deep `$eq` would match
     * here and not on the other SDKs, and a rule that behaves differently per platform is worse
     * than one that is uniformly useless — the opposite trade-off to the `$regex` null case in
     * [RegexOperatorTests], where the reference behaviour produced false *positives*.
     *
     * Note this leaves `$eq` disagreeing with plain equality on the same values, since
     * `{"t": ["a"]}` does compare deeply. The inconsistency is inherited from the reference SDK,
     * which does exactly the same thing, so [testPlainEqualityStillComparesContents] pins it.
     */
    @Test
    fun testEqDoesNotMatchArrayOrObjectAttributes() {
        assertFalse(eval("""{"t": {"${'$'}eq": ["a"]}}""", """{"t": ["a"]}"""))
        assertFalse(eval("""{"t": {"${'$'}eq": {"k": "v"}}}""", """{"t": {"k": "v"}}"""))
    }

    @Test
    fun testPlainEqualityStillComparesContents() {
        assertTrue(eval("""{"t": ["a"]}""", """{"t": ["a"]}"""))
        assertFalse(eval("""{"t": ["a"]}""", """{"t": ["b"]}"""))
        assertTrue(eval("""{"t": {"k": "v"}}""", """{"t": {"k": "v"}}"""))
    }

    @Test
    fun testPairIsAStrictInverseForNonPrimitiveAttributes() {
        assertStrictInverse("""["a"]""", """{"t": ["a"]}""", expectedEq = false)
        assertStrictInverse("""["b"]""", """{"t": ["a"]}""", expectedEq = false)
        assertStrictInverse("""{"k": "v"}""", """{"t": {"k": "v"}}""", expectedEq = false)
        assertStrictInverse("""[]""", """{"t": []}""", expectedEq = false)
    }

    @Test
    fun testPairIsAStrictInverseForNonPrimitiveConditions() {
        assertStrictInverse("""["a"]""", """{"t": "a"}""", expectedEq = false)
        assertStrictInverse("""{"k": "v"}""", """{"t": "a"}""", expectedEq = false)
        assertStrictInverse("""["a"]""", """{"other": "x"}""", expectedEq = false)
    }

    @Test
    fun testPairIsAStrictInverseForPrimitives() {
        assertStrictInverse(""""a"""", """{"t": "a"}""", expectedEq = true)
        assertStrictInverse(""""b"""", """{"t": "a"}""", expectedEq = false)
        assertStrictInverse("25", """{"t": 25}""", expectedEq = true)
        assertStrictInverse("true", """{"t": true}""", expectedEq = true)
        assertStrictInverse("null", """{"t": null}""", expectedEq = true)
        assertStrictInverse("null", """{"other": "x"}""", expectedEq = true)
        assertStrictInverse(""""a"""", """{"other": "x"}""", expectedEq = false)
    }

    /**
     * `$elemMatch` evaluates its body against each element, so the string-only `$eq` also made
     * `{"n": {"$elemMatch": {"$eq": 0}}}` miss `[0]` — the same symptom the reference SDK fixed
     * from the other direction in sdk-js #6323, where falsy elements were skipped before the
     * comparison ran.
     */
    @Test
    fun testElemMatchEqMatchesFalsyNumericElements() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [0]}"""))
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [3, 0, 7]}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [3, 7]}"""))
    }

    @Test
    fun testElemMatchEqMatchesFalsyBooleanAndStringElements() {
        assertTrue(
            eval(
                """{"n": {"${'$'}elemMatch": {"${'$'}eq": false}}}""",
                """{"n": [false]}"""
            )
        )
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": ""}}}""", """{"n": [""]}"""))
    }

    @Test
    fun testElemMatchEqStillMatchesStringElements() {
        assertTrue(
            eval(
                """{"t": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""",
                """{"t": ["a", "b"]}"""
            )
        )
        assertFalse(
            eval(
                """{"t": {"${'$'}elemMatch": {"${'$'}eq": "z"}}}""",
                """{"t": ["a", "b"]}"""
            )
        )
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

    /**
     * Whatever the answer for a given shape, `$eq` and `$ne` must be exact opposites. Both
     * returning false is the signature of the operator never being reached at all, which is what
     * happened for every non-primitive operand while the pair lived in the primitive-attribute
     * branch.
     */
    private fun assertStrictInverse(
        @Language("json") condition: String,
        @Language("json") attributes: String,
        expectedEq: Boolean,
    ) {
        val eq = eval("""{"t": {"${'$'}eq": $condition}}""", attributes)
        val ne = eval("""{"t": {"${'$'}ne": $condition}}""", attributes)

        assertTrue(eq != ne, "\$eq and \$ne both returned $eq for $condition vs $attributes")
        assertEquals(expectedEq, eq, "\$eq for $condition vs $attributes")
    }
}
