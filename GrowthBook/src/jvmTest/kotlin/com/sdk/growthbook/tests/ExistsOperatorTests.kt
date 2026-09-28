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
 * `$exists` and the value it is given.
 *
 * The reference SDK reads that value in a boolean context
 * (`mongrule.ts`: `return expected ? actual != null : actual == null`), so it follows JavaScript
 * truthiness rather than requiring a JSON boolean. Only `true` / `false` used to be read here: any
 * other value matched in neither direction and fell through to the evaluator's trailing false.
 *
 * GrowthBook's own UI only ever writes a boolean, so the shared spec fixtures have no case for
 * anything else. Hence this suite.
 */
class ExistsOperatorTests {

    @Test
    fun testBooleanValuesAreUnchanged() {
        assertTrue(eval("""{"v": {"${'$'}exists": true}}""", """{"v": 1}"""))
        assertFalse(eval("""{"v": {"${'$'}exists": true}}""", """{}"""))
        assertTrue(eval("""{"v": {"${'$'}exists": false}}""", """{}"""))
        assertFalse(eval("""{"v": {"${'$'}exists": false}}""", """{"v": 1}"""))
    }

    /** A stored null is not present, in either direction, as `actual != null` is false for it in JS. */
    @Test
    fun testAStoredNullIsNotPresent() {
        assertFalse(eval("""{"v": {"${'$'}exists": true}}""", """{"v": null}"""))
        assertTrue(eval("""{"v": {"${'$'}exists": false}}""", """{"v": null}"""))
    }

    /** A falsy value — `0`, `""`, `null` — asks for an absent attribute, like `false`. */
    @Test
    fun testFalsyValuesAskForAnAbsentAttribute() {
        for (value in listOf("0", "\"\"", "null")) {
            assertTrue(eval("""{"v": {"${'$'}exists": $value}}""", """{}"""), "\$exists: $value, absent")
            assertFalse(eval("""{"v": {"${'$'}exists": $value}}""", """{"v": 1}"""), "\$exists: $value, present")
        }
    }

    /**
     * Any other value asks for a present attribute, like `true`. That includes the string
     * `"false"`, which is a non-empty string and therefore truthy in JS.
     */
    @Test
    fun testTruthyValuesAskForAPresentAttribute() {
        for (value in listOf("1", "-1", "\"yes\"", "\"false\"", "[]", "{}")) {
            assertTrue(eval("""{"v": {"${'$'}exists": $value}}""", """{"v": 1}"""), "\$exists: $value, present")
            assertFalse(eval("""{"v": {"${'$'}exists": $value}}""", """{}"""), "\$exists: $value, absent")
        }
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
