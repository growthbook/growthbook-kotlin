package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBUrlTarget
import com.sdk.growthbook.utils.GBUrlTargetType
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `experiment.urlPatterns` targeting at the SDK level: the gate in GBExperimentEvaluator plus the
 * URL plumbing around it (builder seed, setUrl, per-call override).
 */
class GBUrlTargetingTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private fun sdk(url: String? = null): GrowthBookSDK =
        GBSDKBuilder(
            apiKey = "url-targeting-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setUrl(url).initialize()

    private fun experiment(
        patterns: List<GBUrlTarget>? = listOf(
            GBUrlTarget(
                type = GBUrlTargetType.SIMPLE,
                pattern = "http://www.example.com/home",
                include = true,
            )
        )
    ) = GBExperiment(
        key = "my-experiment",
        variations = listOf(GBNumber(0), GBNumber(1)),
        weights = listOf(0.1f, 0.9f),
        urlPatterns = patterns,
    )

    @Test
    fun testEnrolsWhenTheUrlIsTargeted() {
        val result = sdk(url = "http://www.example.com/home").run(experiment())

        assertTrue(result.inExperiment)
        assertEquals(1, result.variationId)
    }

    @Test
    fun testSkipsWhenTheUrlIsNotTargeted() {
        val result = sdk(url = "http://www.example.com/pricing").run(experiment())

        assertFalse(result.inExperiment)
        assertEquals(0, result.variationId)
    }

    @Test
    fun testSkipsWhenNoUrlIsSetAtAll() {
        assertFalse(sdk().run(experiment()).inExperiment)
    }

    @Test
    fun testExperimentWithoutUrlPatternsIsUnaffected() {
        assertTrue(sdk().run(experiment(patterns = null)).inExperiment)
    }

    @Test
    fun testSetUrlAppliesToTheNextEvaluation() {
        val sdk = sdk(url = "http://www.example.com/pricing")
        assertFalse(sdk.run(experiment()).inExperiment)

        sdk.setUrl("http://www.example.com/home")
        assertTrue(sdk.run(experiment()).inExperiment)
    }

    @Test
    fun testPerCallUrlWinsOverTheContextUrlAndLeavesItAlone() {
        // getUrlRedirects is the only entry point taking a URL; run() deliberately does not, since
        // no reference SDK has such an overload.
        val sdk = sdk(url = "http://www.example.com/pricing")
        val redirecting = experiment().copy(
            variations = listOf(
                GBJson(emptyMap()),
                GBJson(mapOf("urlRedirect" to GBString("http://www.example.com/home-new"))),
            )
        )

        val results = sdk.getUrlRedirects(
            experiments = listOf(redirecting),
            url = "http://www.example.com/home",
        )

        assertEquals(1, results.size)
        assertEquals("http://www.example.com/home-new", results.first().urlWithParams)
        // The context URL is untouched by the per-call argument.
        assertEquals("http://www.example.com/pricing", sdk.getGBContext().url)
        assertTrue(sdk.getUrlRedirects(experiments = listOf(redirecting)).isEmpty())
    }

    @Test
    fun testForcedVariationDoesNotBypassUrlTargeting() {
        // The URL gate sits ahead of the forced-variation check, as it does in the reference SDK:
        // a forced variation must not enrol the user — and fire an exposure — on an untargeted page.
        val sdk = GBSDKBuilder(
            apiKey = "url-targeting-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setUrl("http://www.example.com/pricing")
            .setForcedVariations(mapOf("my-experiment" to 1))
            .initialize()

        val result = sdk.run(experiment())

        assertFalse(result.inExperiment)
        assertEquals(0, result.variationId)
    }

    @Test
    fun testMalformedUrlTargetDegradesInsteadOfFailingThePayload() {
        // An explicit JSON null on a non-nullable property aborts the decode of the *whole* payload,
        // which would drop every feature to its code default. One unusable target must only cost
        // that target, which then fails closed.
        val sdk = GBSDKBuilder(
            apiKey = "url-targeting-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setInitialPayload(
            """
            {
              "features": { "my-feature": { "defaultValue": "on" } },
              "experiments": [
                {
                  "key": "broken-target",
                  "urlPatterns": [
                    { "type": "simple", "pattern": null, "include": null }
                  ],
                  "weights": [0.1, 0.9],
                  "variations": [{}, { "urlRedirect": "http://www.example.com/home-new" }]
                }
              ]
            }
            """.trimIndent()
        ).setUrl("http://www.example.com/home").initialize()

        // The rest of the payload survived.
        assertEquals(GBString("on"), sdk.feature("my-feature").gbValue)
        assertEquals(1, sdk.getExperiments().size)
        // …and the target itself never matches, so nobody is enrolled by it.
        assertTrue(sdk.getUrlRedirects().isEmpty())
    }

    @Test
    fun testNullIncludeCountsAsAnIncludeRule() {
        // Only an explicit `false` excludes, as in the reference SDK.
        val patterns = listOf(
            GBUrlTarget(GBUrlTargetType.SIMPLE, "http://www.example.com/home", include = null)
        )

        assertTrue(sdk(url = "http://www.example.com/home").run(experiment(patterns)).inExperiment)
        assertFalse(sdk(url = "http://www.example.com/pricing").run(experiment(patterns)).inExperiment)
    }

    @Test
    fun testExcludeRuleWins() {
        val patterns = listOf(
            GBUrlTarget(GBUrlTargetType.SIMPLE, "http://www.example.com/*", include = true),
            GBUrlTarget(GBUrlTargetType.SIMPLE, "http://www.example.com/home", include = false),
        )

        assertTrue(sdk(url = "http://www.example.com/pricing").run(experiment(patterns)).inExperiment)
        assertFalse(sdk(url = "http://www.example.com/home").run(experiment(patterns)).inExperiment)
    }
}
