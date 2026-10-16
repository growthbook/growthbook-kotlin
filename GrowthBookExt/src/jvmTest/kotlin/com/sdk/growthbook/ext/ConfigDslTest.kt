package com.sdk.growthbook.ext

import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.GBFetchOutcome
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfigDslTest {

    @Test
    fun `growthBook builds a usable SDK from required config`() {
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
        }
        assertFalse(sdk.isEnabled("nope"))
    }

    @Test
    fun `growthBook throws when apiKey is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiHost = "host"
                networkDispatcher = MockNetworkDispatcher()
            }
        }
        assertTrue(exception.message!!.contains("apiKey"))
    }

    @Test
    fun `growthBook throws when apiHost is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiKey = "key"
                networkDispatcher = MockNetworkDispatcher()
            }
        }
        assertTrue(exception.message!!.contains("apiHost"))
    }

    @Test
    fun `growthBook throws when networkDispatcher is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiKey = "key"
                apiHost = "host"
            }
        }
        assertTrue(exception.message!!.contains("networkDispatcher"))
    }

    @Test
    fun `growthBook wires initialFeatures and attributes into the SDK`() {
        val feature = GBFeature(
            defaultValue = GBString("generic"),
            rules = listOf(
                GBFeatureRule(
                    condition = Json.parseToJsonElement("""{"country":"UA"}"""),
                    force = GBString("ua-only"),
                ),
            ),
        )
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            // Off, so a stale payload in the real cache directory (~/.growthbook on the JVM)
            // cannot override setInitialFeatures and make this assertion host-dependent.
            cachingEnabled = false
            attributes { "country" to "UA" }
            initialFeatures = mapOf("promo" to feature)
        }

        assertEquals("ua-only", sdk.getStringOrNull("promo"))
    }

    @Test
    fun `growthBook wires the enabled flag into the SDK`() {
        val experiment = GBFeature(
            defaultValue = GBString("control"),
            rules = listOf(
                GBFeatureRule(
                    hashAttribute = "id",
                    coverage = 1f,
                    variations = listOf(GBString("A"), GBString("B"))
                )
            )
        )

        fun buildSdk(isEnabled: Boolean) = growthBook {
            apiKey = "key"; apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
            enabled = isEnabled
            attributes { "id" to "user-123" }
            initialFeatures = mapOf("exp" to experiment)
        }

        assertEquals("control", buildSdk(false).getStringOrNull("exp"))

        assertNotEquals("control", buildSdk(true).getStringOrNull("exp"))
    }

    @Test
    fun `growthBook wires initialPayload into the SDK`() {
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            // Off, so a stale payload in the real cache directory cannot override the seed.
            cachingEnabled = false
            initialPayload = """{"status":200,"features":{"promo":{"defaultValue":"seeded"}}}"""
        }

        assertEquals("seeded", sdk.getStringOrNull("promo"))
    }

    @Test
    fun `growthBook wires fetchStatsHandler into the SDK`() {
        val fetched = CountDownLatch(1)
        val outcomes = mutableListOf<GBFetchOutcome>()

        growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = SucceedingGetDispatcher(
                """{"status":200,"features":{"promo":{"defaultValue":"fetched"}}}"""
            )
            cachingEnabled = false
            fetchStatsHandler = { stats ->
                outcomes += stats.outcome
                fetched.countDown()
            }
        }

        // The fetch runs off the calling thread, so wait rather than assert immediately. The
        // deterministic-dispatcher seam the core suite uses (setCoroutineContext) is internal.
        assertTrue(fetched.await(5, TimeUnit.SECONDS), "fetchStatsHandler was never invoked")
        assertEquals(listOf(GBFetchOutcome.Success), outcomes)
    }

    @Test
    fun `growthBook wires staleTtl and cacheMaxAge into the cache policy`() {
        // The policy requires staleTtl < cacheMaxAge and validates on construction, so an inverted
        // pair proves both values actually reach it rather than being dropped by the DSL.
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiKey = "key"
                apiHost = "host"
                networkDispatcher = MockNetworkDispatcher()
                cachingEnabled = false
                cacheMaxAge = 1_000
                staleTtl = 5_000
            }
        }
        assertTrue(exception.message!!.contains("staleTtl"))
    }

    @Test
    fun `growthBook rejects non-positive refreshInterval and staleTtl`() {
        fun buildWith(block: GrowthBookConfigBuilder.() -> Unit) = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
            block()
        }

        assertTrue(
            assertFailsWith<IllegalArgumentException> { buildWith { refreshInterval = 0 } }
                .message!!.contains("refreshInterval")
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { buildWith { staleTtl = -1 } }
                .message!!.contains("staleTtl")
        )
    }

    @Test
    fun `growthBook accepts serveStaleOnError`() {
        // Its effect — serving an expired cache when the revalidating fetch fails — needs an aged
        // cache entry and a failing network round, which this module cannot stage deterministically
        // (the dispatcher-injection seam is internal to :GrowthBook). Core's cache suite covers the
        // behaviour; ConfigDslCoverageTest guards the wiring; this only pins the DSL surface.
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
            cacheMaxAge = 5_000
            staleTtl = 1_000
            serveStaleOnError = true
            initialFeatures = mapOf("promo" to GBFeature(defaultValue = GBString("seeded")))
        }

        assertEquals("seeded", sdk.getStringOrNull("promo"))
    }

    /** Returns [response] to every GET, so the fetch-stats path is exercised. */
    private class SucceedingGetDispatcher(private val response: String) : NetworkDispatcher {
        override fun consumeGETRequest(
            request: String,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ): Job {
            onSuccess(response)
            return Job()
        }

        override fun consumeSSEConnection(
            url: String,
            sseController: SSEConnectionController?
        ): Flow<Resource<String>> = emptyFlow()

        override fun consumePOSTRequest(
            url: String,
            bodyParams: Map<String, Any>,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ) = Unit
    }
}