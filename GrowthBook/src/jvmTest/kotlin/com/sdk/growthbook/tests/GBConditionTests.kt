package com.sdk.growthbook.tests

import kotlin.test.*
import com.sdk.growthbook.utils.GBCondition
import com.sdk.growthbook.evaluators.GBAttributeType
import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.toGbBoolean
import com.sdk.growthbook.model.toGbString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.kotlinx.serialization.from

class GBConditionTests {

    private lateinit var evalConditions: JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = GBTestHelper.getEvalConditionData()
    }

    @Test
    fun testConditions() {
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {
                val evaluator = GBConditionEvaluator()
                val item2JsonObject = item[2] as? JsonObject
                val attributes = if (item2JsonObject == null) {
                    emptyMap<String, GBValue>()
                } else {
                    HashMap(item2JsonObject.mapValues { GBValue.from(it.value) })
                }

                val conditionObj = GBJson(
                    item[1].jsonObject.mapValues { GBValue.from(it.value) }
                )
                val result: Boolean = if (item.size > 4) {
                    val savedGroupsJsonObject = item[4].jsonObject
                    val savedGroups = savedGroupsJsonObject
                        .mapValues { GBValue.from(it.value) }
                    evaluator.evalCondition(attributes, conditionObj, savedGroups)
                } else {
                    evaluator.evalCondition(attributes, conditionObj, null)
                }

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3] +
                        "\nActual result - " + result + "\n\n"

                if (item[3].toString() == result.toString()) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testInValidConditionObj() {
        val evaluator = GBConditionEvaluator()

        assertFalse(evaluator.isOperatorObject(GBJson(emptyMap())))

        assertEquals(evaluator.getType(null).toString(), GBAttributeType.GbUnknown.toString())

        val userAttributes = mapOf("value" to GBString("test"))
        val gbValue = evaluator.getPath(userAttributes, "key")
        assertTrue(gbValue is GBNull)

        assertTrue(!evaluator.evalConditionValue(GBJson(emptyMap()), null, null))

        assertTrue(
            evaluator.evalOperatorCondition(
                "${"$"}lte",
                GBString("abc"),
                GBString("abc"),
                null
            )
        )

        assertTrue(
            evaluator.evalOperatorCondition(
                "${"$"}gte",
                GBString("abc"),
                GBString("abc"),
                null
            )
        )

        assertTrue(
            evaluator.evalOperatorCondition(
                "${"$"}vlt",
                GBString("0.9.0"),
                GBString("0.10.0"),
                null
            )
        )

    }

    @Test
    fun testConditionFailAttributeDoesNotExist() {
        val attributes = mapOf("country" to GBString("IN"))

        assertEquals(
            false, GBConditionEvaluator().evalCondition(
                attributes,
                GBJson(
                    mapOf("brand" to "KZ".toGbString())
                ),
                null
            )
        )
    }

    @Test
    fun testConditionDoesNotExistAttributeExist() {
        val attributes = mapOf("userId" to GBString("1199"))

/*
        @Language("json")
        val condition = """
            {
              "userId": {
                "${'$'}exists": false
              }
            }
        """.trimIndent()
*/

        assertEquals(
            false, GBConditionEvaluator().evalCondition(
                attributes,
                GBJson(
                    mapOf(
                        "userId" to GBJson(
                            mapOf(
                                "${'$'}exists" to false.toGbBoolean(),
                            )
                        )
                    )
                ),
                null
            )
        )
    }

    @Test
    fun testConditionExistAttributeExist() {
        val attributes = mapOf("userId" to GBString("1199"))

        @Language("json")
        val condition = """
            {
              "userId": {
                "${'$'}exists": true
              }
            }
        """.trimIndent()

        assertEquals(
            true, GBConditionEvaluator().evalCondition(
                attributes,
                Json.decodeFromString(GBCondition.serializer(), condition).jsonObject.let(GBValue::from) as GBJson,
                null
            )
        )
    }

    @Test
    fun testConditionExistAttributeDoesNotExist() {
        val attributes = mapOf("user_id_not_exist" to GBString("1199"))

        @Language("json")
        val condition = """
            {
              "userId": {
                "${'$'}exists": true
              }
            }
        """.trimIndent()

        assertEquals(
            false, GBConditionEvaluator().evalCondition(
                attributes,
                Json.decodeFromString(GBCondition.serializer(), condition).jsonObject.let(GBValue::from) as GBJson,
                null
            )
        )
    }

    @Test
    fun `Be careful when the user of the library passes the type that is absent in JSON`() {
        val evaluator = GBConditionEvaluator()

        val conditionValue = GBValue.from(
            // response from Backend (JSON format)
            JsonPrimitive(576),
        )

        val appUser = AppUser(
            age = 18,

            // Kotlin language has Long type,
            // but JSON format doesn't have it (has a number)
            id = 576L,

            name = "some_name",
        )
        val attributeValue = GBNumber(appUser.id)

        assertTrue(
            evaluator.evalConditionValue(
                conditionValue, attributeValue, null
            )
        )
    }

    private data class AppUser(
        val id: Long,
        val age: Int,
        val name: String,
    )
}
