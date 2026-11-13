package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `$inGroup` / `$notInGroup` across every attribute shape.
 *
 * Both operators used to be reachable only for a primitive attribute, so a multi-value attribute
 * such as `tags: ["a", "b"]` fell through to the function's trailing false and *both* operators
 * answered false for the same input — they cannot both be right. In practice that means a rule
 * written as "everyone except this saved group" matched nobody, the same failure shape as the
 * `$nin` defect in [MembershipOperatorTests]. The reference SDK dispatches both on the operator
 * alone (`mongrule.ts`: `isIn(actual, savedGroups[expected] || [])`), and `isIn` intersects when
 * the attribute is an array.
 *
 * The shared spec fixtures (`cases.json`, spec 0.8.0) carry eight saved-group cases and cover both
 * polarities, but every one of them uses a scalar attribute (`{"id": 1}`, `{"id": "2"}`), so the
 * array shape is only covered here.
 *
 * A `savedGroups` entry may also be a `savedGroupReferencesV2` typed object rather than a bare
 * array, and these operators can only read the list flavour of one. Everything else fails them
 * both closed — the single input shape where the pair is deliberately *not* a negation, so those
 * cases assert through [assertBothFailClosed] rather than [assertExactInverse].
 */
class SavedGroupOperatorTests {

    @Language("json")
    private val savedGroups =
        """{"grp": ["a", "b"], "ids": [1, 2], "empty": [], "mixed": [1, "2", 3]}"""

    /**
     * `savedGroupReferencesV2` entries. `list` names `id` as its attribute while every condition
     * below sits on `tag`, so a test that passes by reading the entry's attribute instead of the
     * one the operator sits on is visible rather than accidentally right.
     */
    @Language("json")
    private val typedSavedGroups =
        """
        {
          "list": {"type": "list", "attributeKey": "id", "values": ["a", "b"]},
          "cond": {"type": "condition", "condition": {"plan": "pro"}},
          "noValues": {"type": "list", "attributeKey": "id"},
          "future": {"type": "segment", "values": ["a", "b"]},
          "broken": null
        }
        """

    @Test
    fun testArrayAttributeOverlappingTheGroupIsAMember() {
        assertExactInverse("""{"tag": ["b", "z"]}""", expectedInGroup = true)
    }

    @Test
    fun testArrayAttributeNotOverlappingTheGroupIsNotAMember() {
        assertExactInverse("""{"tag": ["y", "z"]}""", expectedInGroup = false)
    }

    @Test
    fun testArrayAttributeMatchingTheGroupEntirelyIsAMember() {
        assertExactInverse("""{"tag": ["a", "b"]}""", expectedInGroup = true)
    }

    /** An empty attribute contains nothing, so it intersects nothing. */
    @Test
    fun testEmptyArrayAttributeIsNotAMember() {
        assertExactInverse("""{"tag": []}""", expectedInGroup = false)
    }

    @Test
    fun testArrayAttributeWorksForNumericGroups() {
        assertTrue(eval("""{"id": {"${'$'}inGroup": "ids"}}""", """{"id": [2, 9]}"""))
        assertFalse(eval("""{"id": {"${'$'}notInGroup": "ids"}}""", """{"id": [2, 9]}"""))
        assertFalse(eval("""{"id": {"${'$'}inGroup": "ids"}}""", """{"id": [8, 9]}"""))
        assertTrue(eval("""{"id": {"${'$'}notInGroup": "ids"}}""", """{"id": [8, 9]}"""))
    }

    @Test
    fun testStringAttributeInTheGroup() {
        assertExactInverse("""{"tag": "a"}""", expectedInGroup = true)
    }

    @Test
    fun testStringAttributeOutsideTheGroup() {
        assertExactInverse("""{"tag": "z"}""", expectedInGroup = false)
    }

    @Test
    fun testNumericAttributeInTheGroup() {
        assertTrue(eval("""{"id": {"${'$'}inGroup": "ids"}}""", """{"id": 1}"""))
        assertFalse(eval("""{"id": {"${'$'}notInGroup": "ids"}}""", """{"id": 1}"""))
    }

    /**
     * Membership compares type as well as value: the string `"1"` is not the number `1`.
     *
     * The control assertion carries the weight. On its own a `false` for `"1"` proves nothing — a
     * value simply absent from the group produces the same answer — so the same digits are checked
     * with the right type first.
     */
    @Test
    fun testTypeMismatchIsNotAMember() {
        assertTrue(
            eval("""{"id": {"${'$'}inGroup": "ids"}}""", """{"id": 1}"""),
            "control: the number 1 is a member of [1, 2]",
        )
        assertFalse(eval("""{"id": {"${'$'}inGroup": "ids"}}""", """{"id": "1"}"""))
        assertTrue(eval("""{"id": {"${'$'}notInGroup": "ids"}}""", """{"id": "1"}"""))
    }

    /**
     * The spec's `properly typed data` / `improperly typed data` pair, whose group holds both a
     * string and numbers (`[1, "2", 3]`). Matching is per element, so the same digit answers
     * differently depending on which type the group happens to store.
     */
    @Test
    fun testMixedTypeGroupMatchesElementByElement() {
        assertTrue(eval("""{"id": {"${'$'}inGroup": "mixed"}}""", """{"id": "2"}"""))
        assertFalse(eval("""{"id": {"${'$'}inGroup": "mixed"}}""", """{"id": 2}"""))

        assertTrue(eval("""{"id": {"${'$'}inGroup": "mixed"}}""", """{"id": 3}"""))
        assertFalse(eval("""{"id": {"${'$'}inGroup": "mixed"}}""", """{"id": "3"}"""))
    }

    @Test
    fun testAbsentAttributeIsNotAMember() {
        assertExactInverse("""{"unrelated": "x"}""", expectedInGroup = false)
    }

    @Test
    fun testNullAttributeIsNotAMember() {
        assertExactInverse("""{"tag": null}""", expectedInGroup = false)
    }

    /** An object cannot be a member of a group of scalars, but the negation must still hold. */
    @Test
    fun testObjectAttributeIsNotAMember() {
        assertExactInverse("""{"tag": {"k": "a"}}""", expectedInGroup = false)
    }

    @Test
    fun testUnknownGroupIdTreatsTheGroupAsEmpty() {
        assertFalse(eval("""{"tag": {"${'$'}inGroup": "nope"}}""", """{"tag": "a"}"""))
        assertTrue(eval("""{"tag": {"${'$'}notInGroup": "nope"}}""", """{"tag": "a"}"""))
    }

    @Test
    fun testEmptyGroupHasNoMembers() {
        assertFalse(eval("""{"tag": {"${'$'}inGroup": "empty"}}""", """{"tag": "a"}"""))
        assertTrue(eval("""{"tag": {"${'$'}notInGroup": "empty"}}""", """{"tag": "a"}"""))
    }

    @Test
    fun testUnknownGroupWithAnArrayAttribute() {
        assertFalse(eval("""{"tag": {"${'$'}inGroup": "nope"}}""", """{"tag": ["a", "b"]}"""))
        assertTrue(eval("""{"tag": {"${'$'}notInGroup": "nope"}}""", """{"tag": ["a", "b"]}"""))
    }

    @Test
    fun testTypedListEntryResolvesForBothPolarities() {
        assertTrue(evalTyped("""{"tag": {"${'$'}inGroup": "list"}}""", """{"tag": "a"}"""))
        assertFalse(evalTyped("""{"tag": {"${'$'}notInGroup": "list"}}""", """{"tag": "a"}"""))

        assertFalse(evalTyped("""{"tag": {"${'$'}inGroup": "list"}}""", """{"tag": "z"}"""))
        assertTrue(evalTyped("""{"tag": {"${'$'}notInGroup": "list"}}""", """{"tag": "z"}"""))
    }

    /**
     * A typed entry names its own attribute, but these operators do not read it: they take the
     * attribute from the condition they sit on, as they always have. Only `$savedGroup`, which is
     * not bound to an attribute at all, uses the entry's.
     */
    @Test
    fun testTypedListEntryUsesTheAttributeTheOperatorSitsOn() {
        assertTrue(
            evalTyped("""{"tag": {"${'$'}inGroup": "list"}}""", """{"tag": "a", "id": "z"}"""),
            "the condition sits on `tag`, so `tag` decides",
        )
        assertFalse(
            evalTyped("""{"tag": {"${'$'}inGroup": "list"}}""", """{"tag": "z", "id": "a"}"""),
            "reading the entry's `id` instead would wrongly pass",
        )
    }

    /** The attribute is a member of the group's condition, which makes no difference here. */
    @Test
    fun testConditionEntryFailsBothOperatorsClosed() {
        assertBothFailClosed("cond", """{"tag": "a", "plan": "pro"}""")
    }

    @Test
    fun testUnknownEntryTypeFailsBothOperatorsClosed() {
        assertBothFailClosed("future", """{"tag": "a"}""")
    }

    @Test
    fun testTypedListEntryWithoutValuesFailsBothOperatorsClosed() {
        assertBothFailClosed("noValues", """{"tag": "a"}""")
    }

    /** `{"broken": null}` is present in the map, so it is malformed rather than absent. */
    @Test
    fun testMalformedEntryFailsBothOperatorsClosed() {
        assertBothFailClosed("broken", """{"tag": "a"}""")
    }

    /**
     * The one shape that keeps passing: an id absent from the map behaves like an empty group, so
     * `$notInGroup` passes. Anchored here because the fail-closed rule above is one careless edit
     * away from swallowing it.
     */
    @Test
    fun testAbsentIdStillPassesNotInGroupBesideTypedEntries() {
        assertFalse(evalTyped("""{"tag": {"${'$'}inGroup": "nope"}}""", """{"tag": "a"}"""))
        assertTrue(evalTyped("""{"tag": {"${'$'}notInGroup": "nope"}}""", """{"tag": "a"}"""))
    }

    private fun eval(
        @Language("json") condition: String,
        @Language("json") attributes: String,
        @Language("json") groups: String = savedGroups
    ): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            (GBValue.from(Json.parseToJsonElement(groups)) as GBJson).toMap()
        )

    private fun evalTyped(
        @Language("json") condition: String,
        @Language("json") attributes: String
    ): Boolean = eval(condition, attributes, typedSavedGroups)

    private fun inGroup(@Language("json") attributes: String): Boolean =
        eval("""{"tag": {"${'$'}inGroup": "grp"}}""", attributes)

    private fun notInGroup(@Language("json") attributes: String): Boolean =
        eval("""{"tag": {"${'$'}notInGroup": "grp"}}""", attributes)

    /**
     * The pair must stay a true logical negation for every input. A shape that returns false from
     * both is the signature of the operator never being reached at all.
     */
    private fun assertExactInverse(@Language("json") attributes: String, expectedInGroup: Boolean) {
        val member = inGroup(attributes)
        val notMember = notInGroup(attributes)

        assertTrue(
            member != notMember,
            "\$inGroup and \$notInGroup both returned $member for $attributes",
        )
        assertEquals(
            member,
            expectedInGroup,
            "\$inGroup for $attributes should be $expectedInGroup"
        )
    }

    /**
     * The exception to [assertExactInverse]: an entry neither operator can read answers false for
     * both. Reading it as an empty list instead would keep the pair a negation and pass every
     * user through an exclusion rule, which is the failure worth guarding against.
     */
    private fun assertBothFailClosed(groupId: String, @Language("json") attributes: String) {
        assertFalse(
            evalTyped("""{"tag": {"${'$'}inGroup": "$groupId"}}""", attributes),
            "\$inGroup should fail closed for `$groupId`",
        )
        assertFalse(
            evalTyped("""{"tag": {"${'$'}notInGroup": "$groupId"}}""", attributes),
            "\$notInGroup should fail closed for `$groupId` — an empty list would pass everyone",
        )
    }
}
