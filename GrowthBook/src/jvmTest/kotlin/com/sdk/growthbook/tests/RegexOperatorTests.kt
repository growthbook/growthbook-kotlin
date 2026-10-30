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
    fun testInvalidPatternIsNotAMatch() {
        assertFalse(eval("""{"ua": {"${'$'}regex": "("}}""", """{"ua": "anything"}"""))
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

    /** The pattern side stays strict: a non-string pattern is no match, as in the reference SDK. */
    @Test
    fun testNonStringPatternIsNotAMatch() {
        assertFalse(eval("""{"v": {"${'$'}regex": 12}}""", """{"v": "123"}"""))
        assertFalse(eval("""{"v": {"${'$'}regex": 12}}""", """{"v": 123}"""))
    }

    private fun eval(@Language("json") condition: String, @Language("json") attributes: String): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            null
        )
}
