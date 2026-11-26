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
 * Covers `$in` / `$ini` / `$nin` / `$nini` across every combination of attribute presence.
 *
 * The pair must stay a true logical negation: an attribute the user does not have is genuinely
 * not in the list, so `$in` is false and `$nin` is true. Returning false for both — which is what
 * happens when the array-operator branch is gated on the attribute being non-null and evaluation
 * drops out of it — silently breaks any rule written as an exclusion, such as "serve everyone
 * except these countries".
 *
 * The shared spec fixtures (`cases.json`, spec 0.7.1) pin `$in` with a missing attribute
 * ("missing attribute - fail") but contain no `$nin` case with a missing or null attribute, so
 * that half is only covered here. Reported in growthbook-swift#185, where the same inputs were
 * measured against growthbook-js 1.7.0 and this SDK, which both return true.
 */
class MembershipOperatorTests {

    @Test
    fun testInMatchesListedValue() {
        assertTrue(isIn("""{"country": "RU"}"""))
    }

    @Test
    fun testInDoesNotMatchUnlistedValue() {
        assertFalse(isIn("""{"country": "US"}"""))
    }

    @Test
    fun testNotInDoesNotMatchListedValue() {
        assertFalse(notIn("""{"country": "RU"}"""))
    }

    @Test
    fun testNotInMatchesUnlistedValue() {
        assertTrue(notIn("""{"country": "US"}"""))
    }

    @Test
    fun testInDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(
            isIn("""{"unrelated": "x"}"""),
            "A user without the attribute is not in the list, so \$in must not match"
        )
    }

    @Test
    fun testNotInMatchesWhenAttributeIsAbsent() {
        assertTrue(
            notIn("""{"unrelated": "x"}"""),
            "A user without the attribute is not in the list, so \$nin must match"
        )
    }

    @Test
    fun testInDoesNotMatchWhenAttributeIsNull() {
        assertFalse(isIn("""{"country": null}"""))
    }

    @Test
    fun testNotInMatchesWhenAttributeIsNull() {
        assertTrue(
            notIn("""{"country": null}"""),
            "null is not a member of the list, so \$nin must match"
        )
    }

    @Test
    fun testIniDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(eval("""{"country": {"${'$'}ini": ["ru"]}}""", """{"unrelated": "x"}"""))
    }

    @Test
    fun testNiniMatchesWhenAttributeIsAbsent() {
        assertTrue(eval("""{"country": {"${'$'}nini": ["ru"]}}""", """{"unrelated": "x"}"""))
    }

    @Test
    fun testIniDoesNotMatchWhenAttributeIsNull() {
        assertFalse(eval("""{"country": {"${'$'}ini": ["ru"]}}""", """{"country": null}"""))
    }

    @Test
    fun testNiniMatchesWhenAttributeIsNull() {
        assertTrue(eval("""{"country": {"${'$'}nini": ["ru"]}}""", """{"country": null}"""))
    }

    @Test
    fun testIniStillMatchesRegardlessOfCase() {
        assertTrue(eval("""{"country": {"${'$'}ini": ["ru"]}}""", """{"country": "RU"}"""))
    }

    @Test
    fun testNiniStillNegatesACaseInsensitiveMatch() {
        assertFalse(eval("""{"country": {"${'$'}nini": ["ru"]}}""", """{"country": "RU"}"""))
    }

    /**
     * `$all` asks whether every listed value is present in the attribute. An attribute the user
     * does not have contains nothing, so this stays false — it is not a negation and must not
     * follow `$nin` out of the gate.
     */
    @Test
    fun testAllDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(eval("""{"tags": {"${'$'}all": ["a"]}}""", """{"unrelated": "x"}"""))
    }

    @Test
    fun testAlliDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(eval("""{"tags": {"${'$'}alli": ["a"]}}""", """{"unrelated": "x"}"""))
    }

    @Test
    fun testNotInMatchesArrayAttributeWithNoOverlap() {
        assertTrue(eval("""{"tags": {"${'$'}nin": ["a", "b"]}}""", """{"tags": ["c", "d"]}"""))
    }

    @Test
    fun testNotInDoesNotMatchArrayAttributeWithOverlap() {
        assertFalse(eval("""{"tags": {"${'$'}nin": ["a", "b"]}}""", """{"tags": ["c", "a"]}"""))
    }

    @Test
    fun testAndFormMatchesWhenTheGatedAttributeIsAbsent() {
        assertTrue(
            excludeBoth("""{"country": "US"}"""),
            "A logged-out user has no plan, so the plan exclusion holds and the rule applies"
        )
    }

    @Test
    fun testAndFormStillExcludesOnThePresentAttribute() {
        assertFalse(
            excludeBoth("""{"country": "RU"}"""),
            "The country exclusion must still bite while plan is absent"
        )
    }

    @Test
    fun testAndFormStillExcludesOnTheGatedAttributeOnceItAppears() {
        assertFalse(
            excludeBoth("""{"plan": "trial", "country": "US"}"""),
            "Once the user authenticates, the plan exclusion must bite"
        )
    }

    @Test
    fun testAndFormMatchesWhenBothAttributesAreOutsideTheLists() {
        assertTrue(excludeBoth("""{"plan": "pro", "country": "US"}"""))
    }

    @Test
    fun testNotInBelowTheSetThresholdIsUnaffectedByAbsentAttribute() {
        assertTrue(notInList(15, """{"unrelated": "x"}"""))
        assertTrue(notInList(15, """{"country": null}"""))
        assertTrue(notInList(15, """{"country": "ZZ"}"""))
        assertFalse(notInList(15, """{"country": "C3"}"""))
    }

    @Test
    fun testNotInAtAndAboveTheSetThresholdIsUnaffectedByAbsentAttribute() {
        for (size in listOf(16, 20)) {
            assertTrue(notInList(size, """{"unrelated": "x"}"""), "size=$size, absent")
            assertTrue(notInList(size, """{"country": null}"""), "size=$size, null")
            assertTrue(notInList(size, """{"country": "ZZ"}"""), "size=$size, unlisted")
            assertFalse(notInList(size, """{"country": "C3"}"""), "size=$size, listed")
        }
    }

    @Test
    fun testInAtTheSetThresholdStillFailsForAbsentAttribute() {
        assertFalse(
            eval(
                """{"country": {"${'$'}in": ${countries(20)}}}""",
                """{"unrelated": "x"}"""
            )
        )
        assertFalse(
            eval(
                """{"country": {"${'$'}in": ${countries(20)}}}""",
                """{"country": null}"""
            )
        )
        assertTrue(eval("""{"country": {"${'$'}in": ${countries(20)}}}""", """{"country": "C3"}"""))
    }

    @Test
    fun testNotInMatchesWhenTheWholeParentObjectIsAbsent() {
        assertTrue(notInNested("""{"unrelated": "x"}"""))
    }

    @Test
    fun testNotInMatchesWhenTheLeafIsAbsentFromAnExistingParent() {
        assertTrue(notInNested("""{"user": {"id": "1199"}}"""))
    }

    @Test
    fun testNotInMatchesWhenTheLeafIsNull() {
        assertTrue(notInNested("""{"user": {"country": null}}"""))
    }

    @Test
    fun testNotInStillExcludesOnANestedValue() {
        assertFalse(notInNested("""{"user": {"country": "RU"}}"""))
    }

    @Test
    fun testInDoesNotMatchWhenTheParentObjectIsAbsent() {
        assertFalse(
            eval(
                """{"user.country": {"${'$'}in": ["RU", "CN"]}}""",
                """{"unrelated": "x"}"""
            )
        )
    }

    /**
     * `$not` re-enters the same operator evaluation, so a double negation has to land on the exact
     * inverse of the direct operator rather than on a shared false.
     */
    @Test
    fun testNotOfInMatchesWhenAttributeIsAbsent() {
        assertTrue(
            eval(
                """{"country": {"${'$'}not": {"${'$'}in": ["RU", "CN"]}}}""",
                """{"unrelated": "x"}"""
            )
        )
    }

    @Test
    fun testNotOfNotInDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(
            eval(
                """{"country": {"${'$'}not": {"${'$'}nin": ["RU", "CN"]}}}""",
                """{"unrelated": "x"}"""
            )
        )
    }

    @Test
    fun testOrFormMatchesOnTheExclusionBranchWhileTheAttributeIsAbsent() {
        @Language("json")
        val condition = """
            {"${'$'}or": [{"country": {"${'$'}nin": ["RU", "CN"]}}, {"plan": "pro"}]}
        """
        assertTrue(eval(condition, """{"unrelated": "x"}"""))
        assertFalse(eval(condition, """{"country": "RU"}"""))
        assertTrue(eval(condition, """{"country": "RU", "plan": "pro"}"""))
    }

    @Test
    fun testNorFormNegatesTheExclusionWhileTheAttributeIsAbsent() {
        @Language("json")
        val condition = """
            {"${'$'}nor": [{"country": {"${'$'}nin": ["RU", "CN"]}}]}
        """
        assertFalse(eval(condition, """{"unrelated": "x"}"""))
        assertTrue(eval(condition, """{"country": "RU"}"""))
    }

    /**
     * `$exists: false` is the explicit way to target absence. Pairing it with `$nin` must stay
     * consistent: both halves hold for a user without the attribute.
     */
    @Test
    fun testExistsFalsePairsConsistentlyWithNotIn() {
        @Language("json")
        val condition = """
            {"country": {"${'$'}exists": false, "${'$'}nin": ["RU", "CN"]}}
        """
        assertTrue(eval(condition, """{"unrelated": "x"}"""))
        assertFalse(eval(condition, """{"country": "US"}"""))
    }

    /**
     * Nothing is a member of the empty list, so `$nin` holds for every input and `$in` for none —
     * including the absent and null cases, where the answer must not come from a different branch.
     */
    @Test
    fun testNotInAnEmptyListAlwaysMatches() {
        assertTrue(eval("""{"country": {"${'$'}nin": []}}""", """{"unrelated": "x"}"""))
        assertTrue(eval("""{"country": {"${'$'}nin": []}}""", """{"country": null}"""))
        assertTrue(eval("""{"country": {"${'$'}nin": []}}""", """{"country": "RU"}"""))
    }

    @Test
    fun testInAnEmptyListNeverMatches() {
        assertFalse(eval("""{"country": {"${'$'}in": []}}""", """{"unrelated": "x"}"""))
        assertFalse(eval("""{"country": {"${'$'}in": []}}""", """{"country": null}"""))
        assertFalse(eval("""{"country": {"${'$'}in": []}}""", """{"country": "RU"}"""))
    }

    /**
     * `null` is a legitimate list member, not a sentinel for absence. A user without the attribute
     * is in `[null, "RU"]`, exactly as the reference SDK has it — `getPath` there also collapses a
     * missing path to `null` (`mongrule.ts`), so `expected.includes(null)` matches.
     *
     * This is why the absent-attribute behaviour must keep coming from the membership check rather
     * than from an early return on null: short-circuiting `$nin` to true whenever the attribute is
     * `GBNull` would invert both cases below and silently diverge from the other SDKs.
     */
    @Test
    fun testNullIsAMemberOfAListThatContainsIt() {
        assertTrue(eval("""{"country": {"${'$'}in": [null, "RU"]}}""", """{"country": null}"""))
        assertTrue(eval("""{"country": {"${'$'}in": [null, "RU"]}}""", """{"unrelated": "x"}"""))
        assertFalse(eval("""{"country": {"${'$'}nin": [null, "RU"]}}""", """{"country": null}"""))
        assertFalse(eval("""{"country": {"${'$'}nin": [null, "RU"]}}""", """{"unrelated": "x"}"""))
    }

    /** The rule is about membership, not about strings — numbers and booleans behave the same. */
    @Test
    fun testNotInMatchesAnAbsentNumericAttribute() {
        assertTrue(eval("""{"tier": {"${'$'}nin": [1, 2]}}""", """{"unrelated": "x"}"""))
        assertTrue(eval("""{"tier": {"${'$'}nin": [1, 2]}}""", """{"tier": null}"""))
        assertFalse(eval("""{"tier": {"${'$'}nin": [1, 2]}}""", """{"tier": 1}"""))
        assertTrue(eval("""{"tier": {"${'$'}nin": [1, 2]}}""", """{"tier": 3}"""))
    }

    @Test
    fun testNotInMatchesAnAbsentBooleanAttribute() {
        assertTrue(eval("""{"beta": {"${'$'}nin": [true]}}""", """{"unrelated": "x"}"""))
        assertFalse(eval("""{"beta": {"${'$'}nin": [true]}}""", """{"beta": true}"""))
        assertTrue(eval("""{"beta": {"${'$'}nin": [true]}}""", """{"beta": false}"""))
    }

    @Test
    fun testElemMatchDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(
            eval(
                """{"tags": {"${'$'}elemMatch": {"${'$'}eq": "a"}}}""",
                """{"unrelated": "x"}"""
            )
        )
    }

    @Test
    fun testSizeDoesNotMatchWhenAttributeIsAbsent() {
        assertFalse(eval("""{"tags": {"${'$'}size": 0}}""", """{"unrelated": "x"}"""))
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

    private fun isIn(@Language("json") attributes: String): Boolean =
        eval("""{"country": {"${'$'}in": ["RU", "CN"]}}""", attributes)

    private fun notIn(@Language("json") attributes: String): Boolean =
        eval("""{"country": {"${'$'}nin": ["RU", "CN"]}}""", attributes)

    /**
     * The shape that surfaced this in practice (growthbook-swift#185): two exclusions combined,
     * where one attribute only exists after authentication. Every key in a condition object is
     * ANDed, so the absent half must not drag the whole rule to false — and must not loosen it
     * either: the exclusion still has to hold from both sides.
     */
    private fun excludeBoth(@Language("json") attributes: String): Boolean =
        eval(
            """{"plan": {"${'$'}nin": ["trial"]}, "country": {"${'$'}nin": ["RU", "CN"]}}""",
            attributes
        )

    /**
     * `getPath` walks a dotted key and yields `GBNull` as soon as a segment is missing, so an
     * exclusion on a nested attribute has to behave like an absent flat one. This is the shape that
     * bites hardest in practice: the parent object exists only for authenticated users.
     */
    private fun notInNested(@Language("json") attributes: String): Boolean =
        eval("""{"user.country": {"${'$'}nin": ["RU", "CN"]}}""", attributes)

    /**
     * [com.sdk.growthbook.model.GBArray] swaps a linear scan for a lazily built HashSet once the
     * list reaches `MEMBERSHIP_SET_THRESHOLD` (16). That index is a Kotlin-only optimisation with
     * no counterpart in the other SDKs, so nothing upstream would catch it changing the answer for
     * an absent attribute — a `GBNull` lookup has to miss both ways.
     */
    private fun countries(size: Int): String =
        (1..size).joinToString(prefix = "[", postfix = "]") { "\"C$it\"" }

    private fun notInList(size: Int, @Language("json") attributes: String): Boolean =
        eval("""{"country": {"${'$'}nin": ${countries(size)}}}""", attributes)

    /**
     * Membership compares numbers by value, as in the reference SDK where `[25].includes(25.0)`.
     * Both sizes are checked because the hash index behind large lists uses
     * [com.sdk.growthbook.model.GBNumber]'s own equality, which keeps `25` and `25.0` apart; the
     * lookup has to try the other form as well.
     */
    @Test
    fun testNumericMembershipComparesByValueForSmallAndLargeLists() {
        for (size in listOf(3, 20)) {
            val ids = (1..size).joinToString(prefix = "[", postfix = "]")
            assertTrue(eval("""{"id": {"${'$'}in": $ids}}""", """{"id": 2.0}"""), "\$in, size $size")
            assertFalse(eval("""{"id": {"${'$'}nin": $ids}}""", """{"id": 2.0}"""), "\$nin, size $size")
            assertTrue(eval("""{"id": {"${'$'}in": $ids}}""", """{"id": [99, 2.0]}"""), "array \$in, size $size")
            assertFalse(eval("""{"id": {"${'$'}in": $ids}}""", """{"id": 2.5}"""), "non-integral, size $size")
        }
    }

    @Test
    fun testAnIntegerAttributeMatchesAnIntegralDecimalInTheList() {
        assertTrue(eval("""{"id": {"${'$'}in": [1.0, 2.0]}}""", """{"id": 2}"""))
        assertFalse(eval("""{"id": {"${'$'}nin": [1.0, 2.0]}}""", """{"id": 2}"""))
    }
}
