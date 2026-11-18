package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.network.GBRequestHeaders
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBInvalidOptionsException
import com.sdk.growthbook.utils.GBOptionsValidator
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers `apiHostRequestHeaders` / `streamingHostRequestHeaders`: that they reach the dispatcher on
 * every request path, that reserved (SDK-managed) names are rejected up front, and that header
 * values never leak into an error message.
 */
class RequestHeadersTests {

    // initialize() writes the feature cache to disk; keep it out of the developer's home dir.
    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private val gbContext = GBContext(
        apiKey = "test-key",
        enabled = true,
        attributes = mapOf("id" to GBString("user-1")),
        forcedVariations = HashMap(),
        qaMode = false,
        trackingCallback = { _, _ -> },
        encryptionKey = null,
        remoteEval = false,
    )

    /** Records the headers handed to each request path instead of performing a request. */
    private class RecordingHeadersClient : MockNetworkClient(MockResponse.successResponse, null) {
        var getHeaders: Map<String, String>? = null
        var postHeaders: Map<String, String>? = null
        var sseHeaders: Map<String, String>? = null

        override fun consumeGETRequestWithNotModified(
            request: String,
            headers: Map<String, String>,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit,
            onNotModified: () -> Unit
        ): Job {
            getHeaders = headers
            return super.consumeGETRequestWithNotModified(
                request, onSuccess, onError, onNotModified
            )
        }

        override fun consumePOSTRequest(
            url: String,
            headers: Map<String, String>,
            bodyParams: Map<String, Any>,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            postHeaders = headers
            super.consumePOSTRequest(url, bodyParams, onSuccess, onError)
        }

        override fun consumeSSEConnection(
            url: String,
            headers: Map<String, String>,
            sseController: SSEConnectionController?
        ): Flow<Resource<String>> {
            sseHeaders = headers
            return emptyFlow()
        }
    }

    private fun optionsWith(
        apiHostHeaders: Map<String, String> = emptyMap(),
        streamingHostHeaders: Map<String, String> = emptyMap(),
    ) = GBOptions(
        apiHost = "https://example.com",
        streamingHost = null,
        apiHostRequestHeaders = apiHostHeaders,
        streamingHostRequestHeaders = streamingHostHeaders,
    )

    @Test
    fun `apiHostRequestHeaders are passed to the features GET request`() {
        val client = RecordingHeadersClient()
        val dataSource = FeaturesDataSource(
            client, gbContext, optionsWith(apiHostHeaders = mapOf("Authorization" to "Bearer t"))
        )

        dataSource.fetchFeatures(success = {}, failure = {}, onNotModified = {})

        assertEquals(mapOf("Authorization" to "Bearer t"), client.getHeaders)
    }

    @Test
    fun `apiHostRequestHeaders are passed to the remote eval POST request`() {
        val client = RecordingHeadersClient()
        val dataSource = FeaturesDataSource(
            client, gbContext, optionsWith(apiHostHeaders = mapOf("X-Gateway-Key" to "abc"))
        )

        dataSource.fetchRemoteEval(
            params = GBRemoteEvalParams(
                attributes = emptyMap(),
                forcedFeatures = emptyMap(),
                forcedVariations = emptyMap(),
            ),
            success = {},
            failure = {},
        )

        assertEquals(mapOf("X-Gateway-Key" to "abc"), client.postHeaders)
    }

    @Test
    fun `streamingHostRequestHeaders are passed to the SSE connection`() {
        val client = RecordingHeadersClient()
        val dataSource = FeaturesDataSource(
            client, gbContext, optionsWith(streamingHostHeaders = mapOf("X-Stream" to "yes"))
        )

        dataSource.autoRefreshRaw()

        assertEquals(mapOf("X-Stream" to "yes"), client.sseHeaders)
        // The streaming headers must not bleed onto the API-host requests.
        assertEquals(null, client.getHeaders)
    }

    @Test
    fun `api and streaming headers stay separate`() {
        val client = RecordingHeadersClient()
        val dataSource = FeaturesDataSource(
            client,
            gbContext,
            optionsWith(
                apiHostHeaders = mapOf("X-Api" to "1"),
                streamingHostHeaders = mapOf("X-Stream" to "2"),
            )
        )

        dataSource.fetchFeatures(success = {}, failure = {}, onNotModified = {})
        dataSource.autoRefreshRaw()

        assertEquals(mapOf("X-Api" to "1"), client.getHeaders)
        assertEquals(mapOf("X-Stream" to "2"), client.sseHeaders)
    }

    @Test
    fun `unusable headers set directly on GBOptions are dropped before reaching the dispatcher`() {
        val client = RecordingHeadersClient()
        // Bypasses GBSDKBuilder validation, as a consumer using the public GrowthBookSDK
        // constructor with a hand-built GBOptions would.
        val dataSource = FeaturesDataSource(
            client,
            gbContext,
            optionsWith(
                apiHostHeaders = mapOf(
                    "If-None-Match" to "\"forged-etag\"",
                    "cache-control" to "no-store",
                    "  " to "blank-name",
                    "X Bad Name" to "v",
                    "X-Bad-Value" to "abc\ndef",
                    // Not reserved: the SDK never sets User-Agent on these requests.
                    "User-Agent" to "acme-mobile/1.0",
                    " X-Kept " to " kept ",
                )
            )
        )

        dataSource.fetchFeatures(success = {}, failure = {}, onNotModified = {})

        assertEquals(
            mapOf("User-Agent" to "acme-mobile/1.0", "X-Kept" to "kept"),
            client.getHeaders,
        )
    }

    @Test
    fun `builder rejects reserved apiHostRequestHeaders`() {
        for (reserved in listOf("if-none-match", "Cache-Control", "IF-NONE-MATCH")) {
            val error = assertFailsWith<GBInvalidOptionsException> {
                newBuilder().setApiHostRequestHeaders(mapOf(reserved to "value"))
            }
            assertTrue(
                error.message!!.contains("apiHostRequestHeaders"),
                "message should name the offending option: ${error.message}",
            )
            assertTrue(error.message!!.contains(reserved))
        }
    }

    @Test
    fun `builder rejects reserved streamingHostRequestHeaders`() {
        val error = assertFailsWith<GBInvalidOptionsException> {
            newBuilder().setStreamingHostRequestHeaders(mapOf("Cache-Control" to "no-store"))
        }

        assertTrue(error.message!!.contains("streamingHostRequestHeaders"))
        assertEquals(1, error.violations.size)
    }

    @Test
    fun `builder rejects a blank header name`() {
        assertFailsWith<GBInvalidOptionsException> {
            newBuilder().setApiHostRequestHeaders(mapOf("   " to "value"))
        }
    }

    @Test
    fun `header values never appear in the validation error`() {
        val secret = "super-secret-gateway-token"
        val error = assertFailsWith<GBInvalidOptionsException> {
            newBuilder().setApiHostRequestHeaders(mapOf("Cache-Control" to secret))
        }

        assertFalse(
            error.message!!.contains(secret),
            "header values may be credentials and must never be reported",
        )
        assertFalse(error.violations.any { it.contains(secret) })
    }

    @Test
    fun `builder rejects a header value HTTP cannot carry`() {
        // SP and HTAB *are* legal in a field-value ("Bearer token"), so only control
        // characters, line breaks and non-ASCII are rejected.
        for (invalid in listOf("abc\ndef", "abc\rdef", "abc\u0000def", "caf\u00e9")) {
            val error = assertFailsWith<GBInvalidOptionsException>("should reject '$invalid'") {
                newBuilder().setApiHostRequestHeaders(mapOf("X-Token" to invalid))
            }
            assertTrue(error.message!!.contains("X-Token"))
            assertFalse(
                error.message!!.contains(invalid.trim()),
                "the value must never be quoted back: ${error.message}",
            )
        }
    }

    @Test
    fun `builder rejects a header name that is not an HTTP token`() {
        for (invalid in listOf("X Token", "X:Token", "X\nToken")) {
            assertFailsWith<GBInvalidOptionsException>("should reject '$invalid'") {
                newBuilder().setApiHostRequestHeaders(mapOf(invalid to "value"))
            }
        }
    }

    @Test
    fun `builder accepts non reserved headers`() {
        val sdk = newBuilder()
            .setApiHostRequestHeaders(mapOf("Authorization" to "Bearer token"))
            .setStreamingHostRequestHeaders(mapOf("X-Stream-Token" to "stream"))
            .initialize()

        // Reaching here means validation passed and initialize() built an instance.
        assertTrue(sdk.getGBContext().enabled)
    }

    /**
     * A token read from a file or env var routinely arrives with a trailing newline. That is
     * surrounding whitespace, not an injection attempt, so it is stripped rather than rejected.
     */
    @Test
    fun `a surrounding-whitespace header value is accepted and trimmed`() {
        newBuilder().setApiHostRequestHeaders(mapOf("Authorization" to "Bearer token\n"))

        assertEquals(
            mapOf("Authorization" to "Bearer token"),
            GBRequestHeaders.sanitize(mapOf("Authorization" to "Bearer token\n")),
        )
    }

    @Test
    fun `builder accepts a User-Agent header`() {
        newBuilder().setApiHostRequestHeaders(mapOf("User-Agent" to "acme-mobile/1.0"))
    }

    @Test
    fun `builder rejects an invalid streamingHost`() {
        for (invalid in listOf("ftp://cdn.example.com", "https://", "https://bad host")) {
            val error = assertFailsWith<GBInvalidOptionsException>("should reject '$invalid'") {
                newBuilder(streamingHost = invalid).initialize()
            }
            assertTrue(error.message!!.contains("streamingHost"))
        }
    }

    @Test
    fun `builder rejects an invalid apiHost`() {
        for (invalid in listOf("ftp://cdn.example.com", "https://", "https://bad host")) {
            val error = assertFailsWith<GBInvalidOptionsException>("should reject '$invalid'") {
                newBuilder(apiHost = invalid).initialize()
            }
            assertTrue(error.message!!.contains("apiHost"))
        }
    }

    /**
     * Build configs routinely default a host to the empty string. Blank means "not set" — every
     * consumer of it already falls back — so it must not fail the build.
     */
    @Test
    fun `builder treats a blank streamingHost as unset`() {
        for (blank in listOf("", "   ")) {
            newBuilder(streamingHost = blank).initialize()
        }
    }

    @Test
    fun `builder accepts a valid streamingHost with or without a scheme`() {
        for (valid in listOf("https://cdn.example.com", "http://localhost:3100", "cdn.example.com")) {
            newBuilder(streamingHost = valid).initialize()
        }
    }

    @Test
    fun `all violations are reported together`() {
        val violations = GBOptionsValidator.findViolations(
            streamingHost = "ftp://cdn.example.com",
            apiHostRequestHeaders = mapOf("If-None-Match" to "x"),
            streamingHostRequestHeaders = mapOf("Cache-Control" to "y"),
            apiHost = "ftp://api.example.com",
        )

        assertEquals(4, violations.size)
    }

    @Test
    fun `sanitize keeps insertion order and drops reserved names case insensitively`() {
        val sanitized = GBRequestHeaders.sanitize(
            linkedMapOf(
                "A" to "1",
                "IF-NONE-MATCH" to "2",
                "B" to "3",
            )
        )

        assertEquals(listOf("A", "B"), sanitized.keys.toList())
    }

    @Test
    fun `sanitize drops unusable names and values`() {
        val sanitized = GBRequestHeaders.sanitize(
            linkedMapOf(
                "X Bad Name" to "1",
                "X-Bad-Value" to "a\nb",
                "X-Good" to "ok",
            )
        )

        assertEquals(mapOf("X-Good" to "ok"), sanitized)
    }

    private fun newBuilder(
        streamingHost: String? = null,
        apiHost: String = "https://example.com",
    ) = GBSDKBuilder(
        apiKey = "test-key",
        apiHost = apiHost,
        streamingHost = streamingHost,
        networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
        attributes = emptyMap(),
        trackingCallback = { _, _ -> },
    )
}
