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

    private fun createTestUrlBuilder(apiHost: String) =
        FeatureURLBuilder(
            GBOptions(apiHost, null)
        )

    companion object {
        private const val TEST_API_HOST = "https://some.domain"
    }
}
