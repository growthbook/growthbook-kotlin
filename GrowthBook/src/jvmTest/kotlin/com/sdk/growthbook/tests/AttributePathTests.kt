package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dot-separated attribute paths.
 *
 * The walk used to skip a segment it could not descend into rather than stop, so it returned the
 * last value it reached: `user.id` against `{"user": "u_1"}` answered `"u_1"`. A path therefore
 * resolved to a value it does not name, and a rule matched a user who has no such attribute —
 * silently, and in the direction that grants access rather than withholds it.
 *
 * The shared spec fixtures exercise dotted paths only where the truncated value happens to differ
 * from the expected one (`{"address": 123}` is not `"CA"` either way), so the whole corpus passed
 * before the fix. These cases pin the difference directly.
 */
class AttributePathTests {

    @Test
    fun testPathStopsAtAScalarInsteadOfReturningIt() {
        assertEquals(
            GBNull,
            GBConditionEvaluator().getPath(
                mapOf("user" to GBString("u_1")),
                "user.id",
            ),
        )
    }

    @Test
    fun testPathStopsWhenItRunsPastTheLeaf() {
        assertEquals(
            GBNull,
            GBConditionEvaluator().getPath(
                (GBValue.from(Json.parseToJsonElement("""{"a": {"b": "x"}}""")) as GBJson).toMap(),
                "a.b.c",
            ),
        )
    }

    @Test
    fun testAValidPathStillResolves() {
        assertEquals(
            GBString("fido"),
            GBConditionEvaluator().getPath(
                (GBValue.from(
                    Json.parseToJsonElement("""{"pets": {"dog": {"name": "fido"}}}""")
                ) as GBJson).toMap(),
                "pets.dog.name",
            ),
        )
    }

    /** The user has no `user.id`, so a rule naming it must not match on the value of `user`. */
    @Test
    fun testTruncatedPathDoesNotSatisfyAnEqualityRule() {
        assertFalse(eval("""{"user.id": "u_1"}""", """{"user": "u_1"}"""))
        assertTrue(
            eval("""{"user.id": "u_1"}""", """{"user": {"id": "u_1"}}"""),
            "control: the real path still matches",
        )
    }

    /**
     * `$exists` reads the same walk, and this is the direction that matters: a truncated path
     * used to report the attribute as present.
     */
    @Test
    fun testTruncatedPathDoesNotExist() {
        assertTrue(eval("""{"user.id": {"${'$'}exists": false}}""", """{"user": "u_1"}"""))
        assertFalse(eval("""{"user.id": {"${'$'}exists": true}}""", """{"user": "u_1"}"""))
    }

    /**
     * A saved group names the attribute to test, and the name comes from the payload rather than
     * from the rule, so a path that does not resolve must leave the user outside the group.
     */
    @Test
    fun testTruncatedPathDoesNotPlaceAUserInASavedGroup() {
        val groups = """{"g": {"type": "list", "attributeKey": "user.id", "values": ["u_1"]}}"""

        assertFalse(
            evalWithGroups("""{"${'$'}savedGroup": {"id": "g"}}""", """{"user": "u_1"}""", groups)
        )
        assertTrue(
            evalWithGroups(
                """{"${'$'}savedGroup": {"id": "g"}}""",
                """{"user": {"id": "u_1"}}""",
                groups,
            ),
            "control: the real path still places them inside",
        )
    }

    private fun eval(
        @Language("json") condition: String,
        @Language("json") attributes: String
    ): Boolean = evalWithGroups(condition, attributes, null)

    private fun evalWithGroups(
        @Language("json") condition: String,
        @Language("json") attributes: String,
        @Language("json") groups: String?
    ): Boolean =
        GBConditionEvaluator().evalCondition(
            (GBValue.from(Json.parseToJsonElement(attributes)) as GBJson).toMap(),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            groups?.let { (GBValue.from(Json.parseToJsonElement(it)) as GBJson).toMap() },
        )
}
