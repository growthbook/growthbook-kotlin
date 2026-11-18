package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import com.sdk.growthbook.test.FakeGrowthBook
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuspendExtensionsTest {

    // --- Value mapping -------------------------------------------------------------------------
    // Against the fake, whose suspendFeature resolves immediately: these pin the mapping from
    // stored value to requested type, not the waiting. The startup-window behaviour is covered
    // separately below, against the real SDK.

    private fun fake(): IGrowthBookSDK = FakeGrowthBook()
        .enable("new-home")
        .disable("promo-banner")
        .setValue("welcome-copy", "Hello")
        .setValue("max-items", 25)
        .setValue("ratio", 0.75)
        .setValue("config", GBJson(mapOf("theme" to GBString("dark"))))

    @Test
    fun `await accessors read configured values`() = runBlocking {
        val gb = fake()

        assertTrue(gb.awaitEnabled("new-home"))
        assertFalse(gb.awaitEnabled("promo-banner"))
        assertTrue(gb.awaitBoolean("new-home", false))
        assertEquals("Hello", gb.awaitString("welcome-copy", "fallback"))
        assertEquals(25, gb.awaitInt("max-items", 0))
        assertEquals(25L, gb.awaitLong("max-items", 0))
        assertEquals(0.75, gb.awaitDouble("ratio", 0.0))
        assertEquals(GBString("dark"), gb.awaitJson("config")?.get("theme"))
    }

    @Test
    fun `await accessors fall back when the value is missing or the wrong type`() = runBlocking {
        val gb = fake()

        assertEquals("fallback", gb.awaitString("never-configured", "fallback"))
        assertEquals(7, gb.awaitInt("never-configured", 7))
        assertFalse(gb.awaitBoolean("never-configured", false))
        assertNull(gb.awaitJson("never-configured"))

        // Configured, but not readable as the requested type.
        assertEquals("fallback", gb.awaitString("max-items", "fallback"))
        assertEquals(7, gb.awaitInt("welcome-copy", 7))
        assertNull(gb.awaitJson("welcome-copy"))
    }

    @Test
    fun `awaitInt narrows any stored numeric type`() = runBlocking {
        val gb: IGrowthBookSDK = FakeGrowthBook().setValue("max-items", GBNumber(25.9))

        assertEquals(25, gb.awaitInt("max-items", 0))
        assertEquals(25L, gb.awaitLong("max-items", 0))
        assertEquals(25.9, gb.awaitDouble("max-items", 0.0))
    }

    @Test
    fun `awaitEnabled applies the fallback only to unknown features`() = runBlocking {
        val gb = fake()

        assertFalse(gb.awaitEnabled("promo-banner", FallbackStrategy.FAIL_OPEN))
        assertTrue(gb.awaitEnabled("never-configured", FallbackStrategy.FAIL_OPEN))
        assertFalse(gb.awaitEnabled("never-configured", FallbackStrategy.FAIL_CLOSED))
    }

    @Test
    fun `await resolves typed flags like value does`() = runBlocking {
        val gb = fake()

        assertTrue(gb.await(Flag("new-home", false)))
        assertEquals("Hello", gb.await(Flag("welcome-copy", "Default")))
        assertEquals(25, gb.await(Flag("max-items", 10)))
        assertEquals(25L, gb.await(Flag("max-items", 10L)))
        assertEquals(0.75, gb.await(Flag("ratio", 0.0)))
        // Falls back exactly like the synchronous resolution.
        assertEquals(10, gb.await(Flag("never-configured", 10)))
        assertEquals(10, gb.await(Flag("welcome-copy", 10)))
    }

    @Test
    fun `await rejects unsupported flag types`(): Unit = runBlocking {
        val gb = fake()

        val exception = assertFailsWith<IllegalArgumentException> {
            gb.await(Flag("config", listOf("unsupported")))
        }
        assertTrue(exception.message!!.contains("Unsupported Flag type"))
    }

    // --- Startup window ------------------------------------------------------------------------

    /**
     * The reason these helpers exist: before the first payload lands every feature is unknown, so a
     * synchronous read returns the default. The gate makes that deterministic rather than a race —
     * the response cannot arrive until the test releases it.
     */
    @Test
    fun `await waits for the first payload while the synchronous read does not`() {
        val release = CountDownLatch(1)
        val sdk = GBSDKBuilder(
            // A key of its own, so no payload cached by another test or an earlier run can satisfy
            // the fetch before the gate opens.
            apiKey = "ext-suspend-accessors-test",
            apiHost = "host",
            networkDispatcher = GatedGetDispatcher(
                """{"status":200,"features":{"promo":{"defaultValue":"fetched"}}}""",
                release,
            ),
            attributes = emptyMap(),
            trackingCallback = { _, _ -> },
            cachingEnabled = false,
        ).initialize()

        // Nothing has been delivered yet, so the feature is still unknown.
        assertEquals("not-loaded-yet", sdk.getString("promo", "not-loaded-yet"))

        release.countDown()

        assertEquals("fetched", runBlocking { sdk.awaitString("promo", "not-loaded-yet") })
    }

    /** Answers a GET with [response], but only once [release] is counted down. */
    private class GatedGetDispatcher(
        private val response: String,
        private val release: CountDownLatch,
    ) : NetworkDispatcher {
        override fun consumeGETRequest(
            request: String,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ): Job {
            // Off the calling thread, so initialize() is not blocked by the gate.
            Thread {
                release.await()
                onSuccess(response)
            }.start()
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
