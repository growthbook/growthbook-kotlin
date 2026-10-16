package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.serializable_model.SerializableGBExperiment
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vendored `urlRedirect` conformance cases from the cross-SDK spec.
 *
 * They only assert the resolved destination — `inExperiment`, `urlRedirect` and `urlWithParams` —
 * so they apply to every target, not just browsers. Performing the navigation is out of scope for
 * this SDK.
 */
class GBUrlRedirectTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun testUrlRedirectConformanceCases() {
        val failed = mutableListOf<String>()
        val cases = GBTestHelper.getUrlRedirectData()

        for (case in cases) {
            val items = case.jsonArray
            val description = items[0].toString()
            val testContext = GBTestHelper.jsonParser.decodeFromJsonElement(
                GBUrlRedirectContextTest.serializer(), items[1]
            )
            val expected = GBTestHelper.jsonParser.decodeFromJsonElement(
                ListSerializer(GBUrlRedirectExpectation.serializer()), items[2]
            )

            val sdk = GBSDKBuilder(
                apiKey = "url-redirect-tests",
                apiHost = "https://host.com",
                attributes = testContext.attributes.jsonObject.mapValues { GBValue.from(it.value) },
                encryptionKey = null,
                trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
                networkDispatcher = MockNetworkClient(null, null),
                remoteEval = false,
            ).setUrl(testContext.url).initialize()

            val actual = sdk.getUrlRedirects(testContext.experiments.map { it.gbDeserialize() })
                .map {
                    GBUrlRedirectExpectation(
                        inExperiment = it.inExperiment,
                        urlRedirect = it.urlRedirect,
                        urlWithParams = it.urlWithParams,
                    )
                }

            if (actual != expected) {
                failed.add("$description\n  expected: $expected\n  actual:   $actual")
            }
        }

        assertTrue(cases.isNotEmpty(), "no urlRedirect conformance cases were loaded")
        assertEquals(emptyList(), failed, "failing urlRedirect cases")
    }

    @Test
    fun testRedirectExperimentsComeFromThePayload() {
        val payload = """
            {
              "features": {},
              "experiments": [
                {
                  "key": "my-experiment",
                  "urlPatterns": [
                    { "type": "simple", "include": true, "pattern": "http://www.example.com/home" }
                  ],
                  "weights": [0.1, 0.9],
                  "variations": [{}, { "urlRedirect": "http://www.example.com/home-new" }]
                }
              ]
            }
        """.trimIndent()

        val sdk = GBSDKBuilder(
            apiKey = "url-redirect-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setInitialPayload(payload)
            .setUrl("http://www.example.com/home")
            .initialize()

        assertEquals(1, sdk.getExperiments().size)
        assertEquals("my-experiment", sdk.getExperiments().first().key)

        // No experiments argument: the payload's auto-experiments are evaluated.
        val results = sdk.getUrlRedirects()
        assertEquals(1, results.size)
        assertEquals("http://www.example.com/home-new", results.first().urlWithParams)
    }

    @Test
    fun testNonRedirectAutoExperimentsAreNeitherEvaluatedNorTracked() {
        // The payload's auto-experiment list also carries visual-editor experiments — the API gates
        // only redirects behind an SDK capability. Evaluating one would fire its exposure for a
        // variation this SDK never applies.
        val tracked = mutableListOf<String>()
        val payload = """
            {
              "features": {},
              "experiments": [
                {
                  "key": "visual-experiment",
                  "urlPatterns": [
                    { "type": "simple", "include": true, "pattern": "http://www.example.com/home" }
                  ],
                  "weights": [0.1, 0.9],
                  "variations": [{}, { "domMutations": [], "css": ".headline { color: red }" }]
                },
                {
                  "key": "redirect-experiment",
                  "urlPatterns": [
                    { "type": "simple", "include": true, "pattern": "http://www.example.com/home" }
                  ],
                  "weights": [0.1, 0.9],
                  "variations": [{}, { "urlRedirect": "http://www.example.com/home-new" }]
                }
              ]
            }
        """.trimIndent()

        val sdk = GBSDKBuilder(
            apiKey = "url-redirect-tests",
            apiHost = "https://host.com",
            attributes = mapOf<String, GBValue>("id" to GBString("1")),
            encryptionKey = null,
            trackingCallback = { experiment: GBExperiment, _: GBExperimentResult? ->
                tracked.add(experiment.key)
            },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        ).setInitialPayload(payload)
            .setUrl("http://www.example.com/home")
            .initialize()

        val results = sdk.getUrlRedirects()

        assertEquals(listOf("redirect-experiment"), tracked)
        assertEquals(1, results.size)
        assertEquals("redirect-experiment", results.first().experiment.key)
        assertEquals("http://www.example.com/home-new", results.first().urlWithParams)
        // Still reported by getExperiments(): that returns the payload as received.
        assertEquals(2, sdk.getExperiments().size)
    }

    @Test
    fun testNoRedirectWhenTheUrlIsNotTargeted() {
        val sdk = sdkWithRedirect(pattern = "http://www.example.com/home")

        assertEquals(emptyList(), sdk.getUrlRedirects(url = "http://www.example.com/pricing"))
    }

    @Test
    fun testNoRedirectWhenAlreadyOnTheDestination() {
        // The patterns match the redirect target too, so the user is already where the experiment
        // would send them — enrolled, but with no redirect to apply.
        val sdk = sdkWithRedirect(pattern = "http://www.example.com/*")

        val results = sdk.getUrlRedirects(url = "http://www.example.com/home")

        assertEquals(1, results.size)
        assertEquals("http://www.example.com/home-new", results.first().urlRedirect)
        assertEquals("", results.first().urlWithParams)
    }

    private fun sdkWithRedirect(pattern: String) = GBSDKBuilder(
        apiKey = "url-redirect-tests",
        apiHost = "https://host.com",
        attributes = mapOf<String, GBValue>("id" to GBString("1")),
        encryptionKey = null,
        trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
        networkDispatcher = MockNetworkClient(null, null),
        remoteEval = false,
    ).setInitialPayload(
        """
        {
          "features": {},
          "experiments": [
            {
              "key": "my-experiment",
              "urlPatterns": [
                { "type": "simple", "include": true, "pattern": "$pattern" }
              ],
              "weights": [0.1, 0.9],
              "variations": [{}, { "urlRedirect": "http://www.example.com/home-new" }]
            }
          ]
        }
        """.trimIndent()
    ).initialize()

    @Serializable
    class GBUrlRedirectContextTest(
        val attributes: JsonElement = JsonObject(HashMap()),
        val url: String? = null,
        val experiments: List<SerializableGBExperiment> = emptyList(),
    )

    @Serializable
    data class GBUrlRedirectExpectation(
        val inExperiment: Boolean = false,
        val urlRedirect: String? = null,
        val urlWithParams: String = "",
    )
}
