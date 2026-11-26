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
 * `$elemMatch` element selection.
 *
 * Two opposite mistakes are possible here, and the suite has to pin both sides:
 *
 * 1. **Testing null elements.** The reference SDK skips them, so `[null]` satisfies nothing. We
 *    used to test every element, which made any negation-flavoured body — `$ne`, `$nin`,
 *    `$exists: false`, `$eq: null` — match an array that merely contained a null.
 * 2. **Skipping falsy elements.** `0`, `false` and `""` are ordinary values. Guarding the loop on
 *    truthiness rather than nullness is exactly the defect the reference SDK carried until
 *    sdk-js #6323, where `{"nums": {"$elemMatch": {"$eq": 0}}}` could not match `[0]`.
 *
 * Neither side is covered by the shared spec fixtures (`cases.json`, spec 0.8.0): its eight
 * `$elemMatch` cases all use string elements and non-null arrays, and the #6323 cases went into
 * sdk-js's own test file rather than the shared suite. So a regression in either direction would
 * pass the spec-driven suite in silence.
 */
class ElemMatchOperatorTests {

    @Test
    fun testNullElementDoesNotSatisfyAnEqualityOnNull() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": null}}}""", """{"n": [null]}"""))
    }

    @Test
    fun testNullElementDoesNotSatisfyExistsFalse() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}exists": false}}}""", """{"n": [null]}"""))
    }

    /**
     * The shape that makes this matter in practice: an exclusion inside `$elemMatch`. A null
     * element is not a member of the list, so testing it would report a match for an array that
     * holds nothing but nulls.
     */
    @Test
    fun testNullElementDoesNotSatisfyAnExclusion() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}nin": ["a"]}}}""", """{"n": [null]}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}ne": "a"}}}""", """{"n": [null]}"""))
    }

    @Test
    fun testAnArrayOfOnlyNullsMatchesNothing() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}nin": ["a"]}}}""", """{"n": [null, null]}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}exists": false}}}""", """{"n": [null, null]}"""))
    }

    /**
     * Guards against "fixing" the null skip by widening it to truthiness, which would reintroduce
     * sdk-js #6323 here.
     */
    @Test
    fun testZeroElementIsTested() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [0]}"""))
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [3, 0, 7]}"""))
    }

    @Test
    fun testFalseElementIsTested() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": false}}}""", """{"n": [false]}"""))
    }

    @Test
    fun testEmptyStringElementIsTested() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": ""}}}""", """{"n": [""]}"""))
    }

    @Test
    fun testFalsyElementsAreTestedAlongsideNulls() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": 0}}}""", """{"n": [null, 0]}"""))
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": false}}}""", """{"n": [null, false]}"""))
    }


    @Test
    fun testMatchingElementIsFoundAfterANull() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"n": [null, "a"]}"""))
    }

    @Test
    fun testMatchingElementIsFoundBeforeANull() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"n": ["a", null]}"""))
    }

    @Test
    fun testExclusionStillMatchesOnANonNullElement() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}nin": ["z"]}}}""", """{"n": [null, "a"]}"""))
    }

    @Test
    fun testComparisonOperatorsSeePastANull() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"${'$'}gt": 5}}}""", """{"n": [null, 9]}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}gt": 5}}}""", """{"n": [null, 1]}"""))
    }

    /** A non-operator condition is evaluated against the element as an object, nulls aside. */
    @Test
    fun testObjectConditionSeesPastANull() {
        assertTrue(eval("""{"n": {"${'$'}elemMatch": {"k": "v"}}}""", """{"n": [null, {"k": "v"}]}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"k": "v"}}}""", """{"n": [null, {"k": "other"}]}"""))
    }

    @Test
    fun testNonMatchingElementsReturnFalse() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"n": [null, "b"]}"""))
    }

    @Test
    fun testEmptyArrayMatchesNothing() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"n": []}"""))
    }

    @Test
    fun testNonArrayAttributeMatchesNothing() {
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"n": "a"}"""))
        assertFalse(eval("""{"n": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"other": "x"}"""))
    }

    @Test
    fun testStringElementsStillMatch() {
        assertTrue(eval("""{"t": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""", """{"t": ["a", "b"]}"""))
        assertFalse(eval("""{"t": {"${'$'}elemMatch": {"${'$'}eq": "z"}}}""", """{"t": ["a", "b"]}"""))
    }

    private fun eval(@Language("json") condition: String, @Language("json") attributes: String): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            null
        )
}
