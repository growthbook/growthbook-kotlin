package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBUrlTarget
import com.sdk.growthbook.utils.GBUrlTargetType
import com.sdk.growthbook.utils.url.getQueryStringOverride
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The vendored `getQueryStringOverride` conformance cases, plus the evaluator wiring that applies
 * them (`?my-experiment=1` QA links).
 */
class GBQueryStringOverrideTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private fun sdk(url: String): GrowthBookSDK =
        GBSDKBuilder(
            apiKey = "querystring-override-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setUrl(url).initialize()

    @Test
    fun testQueryStringOverrideConformanceCases() {
        val failed = mutableListOf<String>()
        val cases = GBTestHelper.getQueryStringOverrideData()

        for (case in cases) {
            val items = case.jsonArray
            val description = items[0].jsonPrimitive.content
            val id = items[1].jsonPrimitive.content
            val url = items[2].jsonPrimitive.content
            val numVariations = items[3].jsonPrimitive.int
            val expected = items[4].jsonPrimitive.intOrNull

            val actual = getQueryStringOverride(id, url, numVariations)
            if (actual != expected) {
                failed.add("$description — expected $expected, got $actual")
            }
        }

        assertTrue(cases.isNotEmpty(), "no getQueryStringOverride conformance cases were loaded")
        assertEquals(emptyList(), failed, "failing getQueryStringOverride cases")
    }

    @Test
    fun testQueryStringOverrideWinsOverForcedVariations() {
        // Reference ordering: the querystring override is read before context forced variations.
        val sdk = GBSDKBuilder(
            apiKey = "querystring-override-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setUrl("http://example.com/home?my-experiment=0")
            .setForcedVariations(mapOf("my-experiment" to 1))
            .initialize()

        val result = sdk.run(
            GBExperiment(
                key = "my-experiment",
                variations = listOf(GBNumber(0), GBNumber(1)),
            )
        )

        assertEquals(0, result.variationId)
    }

    @Test
    fun testQueryStringOverrideDoesNotApplyToAnUntargetedUrl() {
        // The URL gate runs first, so a QA link on the wrong page still does not enrol the user.
        val experiment = GBExperiment(
            key = "my-experiment",
            variations = listOf(GBNumber(0), GBNumber(1)),
            urlPatterns = listOf(
                GBUrlTarget(type = GBUrlTargetType.SIMPLE, pattern = "http://example.com/home")
            ),
        )

        assertFalse(sdk("http://example.com/pricing?my-experiment=1").run(experiment).inExperiment)
        assertTrue(sdk("http://example.com/home?my-experiment=1").run(experiment).inExperiment)
    }

    @Test
    fun testNoOverrideWithoutAUrl() {
        val sdk = GBSDKBuilder(
            apiKey = "querystring-override-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setForcedVariations(mapOf("my-experiment" to 1)).initialize()

        val result = sdk.run(
            GBExperiment(
                key = "my-experiment",
                variations = listOf(GBNumber(0), GBNumber(1)),
            )
        )

        assertEquals(1, result.variationId)
    }
}
