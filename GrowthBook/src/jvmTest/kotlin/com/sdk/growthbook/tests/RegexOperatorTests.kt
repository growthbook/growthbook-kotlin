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
 * The `$regex` family against attributes that are not strings.
 *
 * The attribute used to be cast to `GBString`, so anything else failed the match outright and a
 * regex rule on an id or a build number sent as a JSON number could never fire. The reference SDK
 * hands the attribute to `RegExp.prototype.test`, which converts it first.
 *
 * That conversion is JavaScript's `String(value)`, and it is reproduced for every value but one:
 * an array is matched as its elements joined by commas (`["internal", "beta"]` →
 * `"internal,beta"`), an object as `"[object Object]"`.
 *
 * `null` is the one conversion deliberately **not** reproduced, and the tests below pin that
 * decision so it is not "corrected" later by someone diffing against `mongrule.ts`: it would render
 * as the text `"null"`, which makes a pattern like `ull` match a user who has no such attribute at
 * all. That is an artefact of JavaScript's string conversion, not targeting anyone meant to express.
 *
 * The shared spec fixtures (`cases.json`, spec 0.9.0) only ever match a string pattern against a
 * string attribute.
 */
class RegexOperatorTests {

    @Test
    fun testStringAttributeStillMatches() {
        assertTrue(eval("""{"ua": {"${'$'}regex": "Android"}}""", """{"ua": "Android Mobile"}"""))
        assertFalse(eval("""{"ua": {"${'$'}regex": "Android"}}""", """{"ua": "Chrome Desktop"}"""))
    }

    @Test
    fun testCaseSensitivityIsUnchanged() {
        assertFalse(eval("""{"ua": {"${'$'}regex": "android"}}""", """{"ua": "Android"}"""))
        assertTrue(eval("""{"ua": {"${'$'}regexi": "android"}}""", """{"ua": "Android"}"""))
    }

    @Test
    fun testNegatedVariantsAreUnchangedForStrings() {
        assertTrue(eval("""{"ua": {"${'$'}notRegex": "Android"}}""", """{"ua": "Chrome"}"""))
        assertFalse(eval("""{"ua": {"${'$'}notRegex": "Android"}}""", """{"ua": "Android"}"""))
        assertTrue(eval("""{"ua": {"${'$'}notRegexi": "android"}}""", """{"ua": "Chrome"}"""))
    }

    @Test
    fun testInvalidPatternIsNotAMatch() {
        assertFalse(eval("""{"ua": {"${'$'}regex": "("}}""", """{"ua": "anything"}"""))
    }

    @Test
    fun testNumericAttributeIsMatchedAsText() {
        assertTrue(eval("""{"id": {"${'$'}regex": "^12"}}""", """{"id": 123}"""))
        assertFalse(eval("""{"id": {"${'$'}regex": "^9"}}""", """{"id": 123}"""))
    }

    @Test
    fun testNumericAttributeAnchoredWholeMatch() {
        assertTrue(eval("""{"id": {"${'$'}regex": "^123${'$'}"}}""", """{"id": 123}"""))
        assertFalse(eval("""{"id": {"${'$'}regex": "^12${'$'}"}}""", """{"id": 123}"""))
    }

    /** Same rendering rule as the version operators: an integral value carries no `.0`. */
    @Test
    fun testIntegralDecimalRendersWithoutFraction() {
        assertTrue(eval("""{"id": {"${'$'}regex": "^10${'$'}"}}""", """{"id": 10.0}"""))
        assertFalse(eval("""{"id": {"${'$'}regex": "\\."}}""", """{"id": 10.0}"""))
    }

    @Test
    fun testNonIntegralDecimalKeepsItsFraction() {
        assertTrue(eval("""{"id": {"${'$'}regex": "^1\\.5${'$'}"}}""", """{"id": 1.5}"""))
    }

    @Test
    fun testNegativeNumberIsMatchedAsText() {
        assertTrue(eval("""{"d": {"${'$'}regex": "^-3${'$'}"}}""", """{"d": -3}"""))
    }

    /**
     * Ids beyond 2^53 must keep every digit. Normalising an integral attribute through a double
     * first loses precision — `1234567890123456789` comes back as `1234567890123456768` — which
     * would silently rewrite the text the pattern is matched against. Snowflake-style ids are
     * exactly this size, so this is ordinary input, not an edge case.
     */
    @Test
    fun testLargeIntegerIdKeepsEveryDigit() {
        assertTrue(
            eval(
                """{"id": {"${'$'}regex": "^1234567890123456789${'$'}"}}""",
                """{"id": 1234567890123456789}""",
            )
        )
        assertFalse(
            eval(
                """{"id": {"${'$'}regex": "^1234567890123456768${'$'}"}}""",
                """{"id": 1234567890123456789}""",
            )
        )
    }

    @Test
    fun testIntegerJustPastDoublePrecisionKeepsItsValue() {
        assertTrue(
            eval(
                """{"id": {"${'$'}regex": "^9007199254740993${'$'}"}}""",
                """{"id": 9007199254740993}""",
            )
        )
        assertFalse(
            eval(
                """{"id": {"${'$'}regex": "^9007199254740992${'$'}"}}""",
                """{"id": 9007199254740993}""",
            )
        )
    }

    @Test
    fun testNegatedVariantWorksForNumbers() {
        assertTrue(eval("""{"id": {"${'$'}notRegex": "^9"}}""", """{"id": 123}"""))
        assertFalse(eval("""{"id": {"${'$'}notRegex": "^12"}}""", """{"id": 123}"""))
    }

    @Test
    fun testBooleanAttributeIsMatchedAsText() {
        assertTrue(eval("""{"b": {"${'$'}regex": "^true${'$'}"}}""", """{"b": true}"""))
        assertTrue(eval("""{"b": {"${'$'}regex": "^false${'$'}"}}""", """{"b": false}"""))
        assertFalse(eval("""{"b": {"${'$'}regex": "^true${'$'}"}}""", """{"b": false}"""))
    }

    @Test
    fun testBooleanAttributeIsCaseFoldedLikeAnyText() {
        assertFalse(eval("""{"b": {"${'$'}regex": "TRUE"}}""", """{"b": true}"""))
        assertTrue(eval("""{"b": {"${'$'}regexi": "TRUE"}}""", """{"b": true}"""))
    }

    /**
     * A pattern that matches the text `"null"` must not match a user without the attribute. The
     * reference SDK answers true here; reproducing that would turn a typo into a targeting rule.
     */
    @Test
    fun testAbsentAttributeNeverMatches() {
        assertFalse(eval("""{"v": {"${'$'}regex": "ull"}}""", """{"other": "x"}"""))
        assertFalse(eval("""{"v": {"${'$'}regex": ".*"}}""", """{"other": "x"}"""))
    }

    @Test
    fun testNullAttributeNeverMatches() {
        assertFalse(eval("""{"v": {"${'$'}regex": "ull"}}""", """{"v": null}"""))
        assertFalse(eval("""{"v": {"${'$'}regex": ".*"}}""", """{"v": null}"""))
    }

    /** An array is matched as `Array.prototype.join` renders it, as in the reference SDK. */
    @Test
    fun testArrayAttributeIsMatchedAsJoinedText() {
        assertTrue(eval("""{"v": {"${'$'}regex": "^1,2${'$'}"}}""", """{"v": [1, 2]}"""))
        assertTrue(eval("""{"v": {"${'$'}regex": "^internal"}}""", """{"v": ["internal", "beta"]}"""))
        // The joined text starts with the first element only
        assertFalse(eval("""{"v": {"${'$'}regex": "^beta"}}""", """{"v": ["internal", "beta"]}"""))
    }

    /** `join` renders a null element as empty and flattens a nested array into the same text. */
    @Test
    fun testArrayElementsRenderAsJavaScriptJoinsThem() {
        assertTrue(eval("""{"v": {"${'$'}regex": "^1,,a${'$'}"}}""", """{"v": [1, null, "a"]}"""))
        assertTrue(eval("""{"v": {"${'$'}regex": "^1,2,3${'$'}"}}""", """{"v": [[1, 2], 3]}"""))
        assertTrue(eval("""{"v": {"${'$'}regex": "^10,true${'$'}"}}""", """{"v": [10.0, true]}"""))
    }

    @Test
    fun testObjectAttributeIsMatchedAsObjectText() {
        assertTrue(eval("""{"v": {"${'$'}regex": "^\\[object Object\\]${'$'}"}}""", """{"v": {"k": "v"}}"""))
        assertFalse(eval("""{"v": {"${'$'}regex": "k"}}""", """{"v": {"k": "v"}}"""))
    }

    /** The pattern side stays strict: a non-string pattern is no match, as in the reference SDK. */
    @Test
    fun testNonStringPatternIsNotAMatch() {
        assertFalse(eval("""{"v": {"${'$'}regex": 12}}""", """{"v": "123"}"""))
        assertFalse(eval("""{"v": {"${'$'}regex": 12}}""", """{"v": 123}"""))
    }

    /**
     * An unusable *pattern* is the one case where the pair is deliberately not a negation, and the
     * reference SDK agrees: it wraps the match in a try/catch returning false for `$notRegex` too,
     * and a non-string pattern throws on its way into the RegExp constructor. Nothing can be said
     * about a rule whose pattern does not compile, so neither polarity claims anything.
     */
    @Test
    fun testAnUnusablePatternFailsBothPolarities() {
        assertFalse(eval("""{"v": {"${'$'}notRegex": 12}}""", """{"v": "123"}"""))
        assertFalse(eval("""{"v": {"${'$'}notRegex": "("}}""", """{"v": "anything"}"""))
    }

    /**
     * An absent or null attribute is the mirror image: it matches no pattern, so it
     * does-not-match every pattern. Answering false for both made a rule written as "everyone
     * without a corporate email" match nobody — the same self-contradiction as `$eq` / `$ne` and
     * `$inGroup` / `$notInGroup` before they were fixed.
     *
     * The reference SDK arrives elsewhere by stringifying (an absent attribute is looked up as
     * `null`, which becomes the text `"null"`), which [testAbsentAttributeNeverMatches] explains
     * is not reproduced. These assertions pin the negation property, not that route.
     */
    @Test
    fun testNegatedVariantsPassForAnAttributeWithNoText() {
        assertTrue(eval("""{"v": {"${'$'}notRegex": "ull"}}""", """{"other": "x"}"""))
        assertTrue(eval("""{"v": {"${'$'}notRegex": ".*"}}""", """{"v": null}"""))
        assertTrue(eval("""{"v": {"${'$'}notRegexi": "ANYTHING"}}""", """{"other": "x"}"""))
    }

    /**
     * Array and object attributes reach the operator instead of being skipped by an
     * attribute-shape branch, and both polarities agree on their text: "everyone whose tags do
     * not start with internal" excludes a user tagged `internal`, as the reference SDK's
     * `{"$not": {"$regex": …}}` does.
     */
    @Test
    fun testMultiValueAttributesReachTheOperator() {
        val tags = """{"v": ["internal", "beta"]}"""
        assertTrue(eval("""{"v": {"${'$'}regex": "^internal"}}""", tags))
        assertFalse(eval("""{"v": {"${'$'}notRegex": "^internal"}}""", tags))
        assertFalse(eval("""{"v": {"${'$'}not": {"${'$'}regex": "^internal"}}}""", tags))
        assertTrue(eval("""{"v": {"${'$'}notRegex": "^beta"}}""", tags))

        assertTrue(eval("""{"v": {"${'$'}regex": "object"}}""", """{"v": {"k": "v"}}"""))
        assertFalse(eval("""{"v": {"${'$'}notRegex": "object"}}""", """{"v": {"k": "v"}}"""))
    }

    private fun eval(@Language("json") condition: String, @Language("json") attributes: String): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            null
        )
}
