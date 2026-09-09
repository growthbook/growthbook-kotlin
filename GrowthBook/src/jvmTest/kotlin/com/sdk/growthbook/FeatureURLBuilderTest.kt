package com.sdk.growthbook

import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.features.FeatureURLBuilder
import com.sdk.growthbook.utils.FeatureRefreshStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureURLBuilderTest {

    @Test
    fun verifyCreateUrl() {
        val actual = createTestUrlBuilder(TEST_API_HOST)
            .buildUrl("api_key")

        assertEquals("$TEST_API_HOST/api/features/api_key", actual)
    }

    @Test
    fun verifyCreateUrlWithDash() {
        val actual = createTestUrlBuilder("$TEST_API_HOST/")
            .buildUrl("api_key_2")

        assertEquals("$TEST_API_HOST/api/features/api_key_2", actual)
    }

    @Test
    fun verifyCreateSseUrl() {
        val urlBuilder = FeatureURLBuilder(
            GBOptions(TEST_API_HOST, TEST_API_HOST)
        )
        val actual = urlBuilder.buildUrl(
            "api_key", FeatureRefreshStrategy.SERVER_SENT_EVENTS
        )

        assertEquals("$TEST_API_HOST/sub/api_key", actual)
    }

    @Test
    fun verifySseUrlFallsBackToApiHostWhenStreamingHostIsNotSet() {
        val actual = createTestUrlBuilder(TEST_API_HOST)
            .buildUrl("api_key", FeatureRefreshStrategy.SERVER_SENT_EVENTS)

        assertEquals("$TEST_API_HOST/sub/api_key", actual)
    }

    @Test
    fun verifySseUrlFallsBackToApiHostWhenStreamingHostIsBlank() {
        val urlBuilder = FeatureURLBuilder(GBOptions(TEST_API_HOST, "   "))

        val actual = urlBuilder.buildUrl("api_key", FeatureRefreshStrategy.SERVER_SENT_EVENTS)

        assertEquals("$TEST_API_HOST/sub/api_key", actual)
    }

    @Test
    fun verifyStreamingHostIsUsedForSseOnly() {
        val urlBuilder = FeatureURLBuilder(
            GBOptions(TEST_API_HOST, TEST_STREAMING_HOST)
        )

        assertEquals(
            "$TEST_STREAMING_HOST/sub/api_key",
            urlBuilder.buildUrl("api_key", FeatureRefreshStrategy.SERVER_SENT_EVENTS),
        )
        assertEquals(
            "$TEST_API_HOST/api/features/api_key",
            urlBuilder.buildUrl("api_key"),
        )
    }

    @Test
    fun verifySchemeLessHostIsResolvedAgainstHttps() {
        assertEquals(
            "https://some.domain/api/features/api_key",
            createTestUrlBuilder("some.domain").buildUrl("api_key"),
        )
        assertEquals(
            "https://cdn.example.com/sub/api_key",
            FeatureURLBuilder(GBOptions(TEST_API_HOST, "cdn.example.com"))
                .buildUrl("api_key", FeatureRefreshStrategy.SERVER_SENT_EVENTS),
        )
    }

    @Test
    fun verifyExplicitHttpSchemeIsPreserved() {
        assertEquals(
            "http://localhost:3100/api/features/api_key",
            createTestUrlBuilder("http://localhost:3100").buildUrl("api_key"),
        )
    }

    /**
     * A blank apiHost must stay scheme-less. `https:/api/features/<key>` is not rejected by HTTP
     * clients — OkHttp reads `api` as the host — so an apiHost left empty by a build config would
     * quietly send the client key somewhere real instead of failing.
     */
    @Test
    fun verifyBlankApiHostDoesNotProduceASchemeOnlyUrl() {
        val actual = createTestUrlBuilder("")
            .buildUrl("api_key")

        assertEquals("/api/features/api_key", actual)
    }

    @Test
    fun verifyRedundantTrailingSlashesAreCollapsed() {
        val actual = createTestUrlBuilder("$TEST_API_HOST///")
            .buildUrl("api_key")

        assertEquals("$TEST_API_HOST/api/features/api_key", actual)
    }

    private fun createTestUrlBuilder(apiHost: String) =
        FeatureURLBuilder(
            GBOptions(apiHost, null)
        )

    companion object {
        private const val TEST_API_HOST = "https://some.domain"
        private const val TEST_STREAMING_HOST = "https://streaming.some.domain"
    }
}
