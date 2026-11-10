package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.sandbox.CachingJvm
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shared spec's `savedGroupReferencesRemote` section (growthbook/growthbook#7277): saved groups
 * whose members live outside the payload, resolved by the app into the user's remote group ids.
 *
 * The `feature` cases run through a real [GrowthBookSDK] seeded from a payload, not the evaluator
 * alone, because their point is how `remoteGroupIds` on the context reaches evaluation.
 */
class GBSavedGroupReferencesRemoteTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    // Cache reads happen even with caching off, so point them at an empty dir per test
    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun testConditions() {
        val section = GBTestHelper.getSavedGroupReferencesRemoteData("evalCondition")
        val failed = ArrayList<String>()

        for (item in section) {
            if (item !is JsonArray) continue

            val name = item[0].jsonPrimitive.content
            val condition = GBValue.from(item[1]) as? GBJson ?: GBJson(emptyMap())
            val attributes = (GBValue.from(item[2]) as? GBJson)?.toMap() ?: emptyMap()
            val expected = item[3].jsonPrimitive.booleanOrNull
            val savedGroups = if (item.size > 4) (GBValue.from(item[4]) as? GBJson)?.toMap() else null

            val actual = GBConditionEvaluator().evalCondition(attributes, condition, savedGroups)
            if (actual != expected) {
                failed.add("$name — expected $expected, got $actual")
            }
        }

        report("evalCondition", section.size, failed)
    }

    @Test
    fun testFeatures() {
        val section = GBTestHelper.getSavedGroupReferencesRemoteData("feature")
        val failed = ArrayList<String>()

        for (item in section) {
            if (item !is JsonArray) continue

            val name = item[0].jsonPrimitive.content
            val context = item[1].jsonObject
            val attributes = context["attributes"]?.jsonObject?.mapValues { GBValue.from(it.value) }
                ?: emptyMap()
            val remoteGroupIds = context["remoteGroupIds"]?.jsonArray?.map { it.jsonPrimitive.content }
            val payload = buildJsonObject {
                context["features"]?.let { put("features", it) }
                context["savedGroups"]?.let { put("savedGroups", it) }
            }

            val actual = seed(payload.toString(), attributes, remoteGroupIds)
                .feature(item[2].jsonPrimitive.content)
            val expected = item[3].jsonObject
            val actualValue = actual.gbValue?.gbSerialize() as? JsonPrimitive

            if (
                actualValue != expected["value"] ||
                actual.on != expected["on"]?.jsonPrimitive?.booleanOrNull ||
                actual.off != expected["off"]?.jsonPrimitive?.booleanOrNull ||
                actual.source.toString() != expected["source"]?.jsonPrimitive?.content ||
                actual.ruleId != expected["ruleId"]?.jsonPrimitive?.content
            ) {
                failed.add(
                    "$name — expected $expected, got value=$actualValue on=${actual.on} " +
                        "source=${actual.source} ruleId=${actual.ruleId}"
                )
            }
        }

        report("feature", section.size, failed)
    }

    @Test
    fun setRemoteGroupIdsChangesEvaluationAtRuntime() {
        val sdk = seed(PAYLOAD, mapOf("account_id" to GBString("a1")), remoteGroupIds = null)

        assertFalse(sdk.feature("flag").on, "unresolved groups match nobody")
        assertFalse(sdk.feature("excluded").on, "unresolved groups fail \"not in group\" too")

        sdk.setRemoteGroupIds(listOf("grp_vip"))
        assertTrue(sdk.feature("flag").on)
        assertFalse(sdk.feature("excluded").on)
        assertEquals(listOf("grp_vip"), sdk.getRemoteGroupIds())

        // An empty list means "in no group", which is what lets "not in group" match
        sdk.setRemoteGroupIds(emptyList())
        assertFalse(sdk.feature("flag").on)
        assertTrue(sdk.feature("excluded").on)

        sdk.setRemoteGroupIds(null)
        assertFalse(sdk.feature("flag").on)
        assertFalse(sdk.feature("excluded").on)
    }

    @Test
    fun remoteEvalSendsTheRemoteGroupIds() = runTest {
        val client = CapturingPostNetworkClient(MockResponse.successResponse)
        val sdk = GBSDKBuilder(
            "test-key",
            "https://cdn.growthbook.io",
            attributes = mapOf("account_id" to GBString("a1")),
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = client,
            remoteEval = true,
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        sdk.setRemoteGroupIds(listOf("grp_vip"))

        val body = assertNotNull(client.lastBodyParams)

        @Suppress("UNCHECKED_CAST")
        val attributes = body["attributes"] as Map<String, Any?>
        assertEquals(JsonArray(listOf(JsonPrimitive("grp_vip"))), attributes["__gb_remoteGroupIds"])
        assertEquals(JsonPrimitive("a1"), attributes["account_id"])
    }

    @Test
    fun inGroupOnARemoteEntryIsUnsupported() {
        val savedGroups = mapOf("grp_vip" to json("""{"type": "remote", "attributeKey": "account_id"}"""))
        val attributes = mapOf(
            "account_id" to GBString("a1"),
            "__gb_remoteGroupIds" to json("""["grp_vip"]"""),
        )
        val evaluator = GBConditionEvaluator()

        // $inGroup takes values from the payload, and a remote entry has none: unsupported, so it
        // fails, and fails under $not as well
        assertFalse(evaluator.evalCondition(attributes, json("""{"account_id": {"${'$'}inGroup": "grp_vip"}}""") as GBJson, savedGroups))
        assertFalse(evaluator.evalCondition(attributes, json("""{"${'$'}not": {"account_id": {"${'$'}inGroup": "grp_vip"}}}""") as GBJson, savedGroups))
    }

    private fun json(text: String) = GBValue.from(Json.parseToJsonElement(text))

    // Caching stays off so evaluation sees the seed alone
    private fun seed(
        payload: String,
        attributes: Map<String, GBValue>,
        remoteGroupIds: List<String>?,
    ): GrowthBookSDK = GBSDKBuilder(
        "test-key",
        "https://cdn.growthbook.io",
        attributes = attributes,
        trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
        networkDispatcher = MockNetworkClient(null, null),
        cachingEnabled = false,
    )
        .setInitialPayload(payload)
        .apply { remoteGroupIds?.let { setRemoteGroupIds(it) } }
        .initialize()

    private fun report(section: String, total: Int, failed: List<String>) {
        println("savedGroupReferencesRemote.$section — $total cases, ${failed.size} failed")
        failed.forEach { println("  $it") }
        assertTrue(failed.isEmpty(), "${failed.size} of $total $section cases failed")
    }

    private companion object {
        // "flag" is on for members of grp_vip; "excluded" is on for resolved non-members, with the
        // $exists guard GrowthBook adds to "not in group" rules
        val PAYLOAD = """
            {
              "savedGroups": {"grp_vip": {"type": "remote", "attributeKey": "account_id"}},
              "features": {
                "flag": {"defaultValue": false, "rules": [
                  {"condition": {"${'$'}savedGroup": {"id": "grp_vip"}}, "force": true}
                ]},
                "excluded": {"defaultValue": false, "rules": [
                  {"condition": {
                    "${'$'}not": {"${'$'}savedGroup": {"id": "grp_vip"}},
                    "__gb_remoteGroupIds": {"${'$'}exists": true}
                  }, "force": true}
                ]}
              }
            }
        """.trimIndent()
    }
}
