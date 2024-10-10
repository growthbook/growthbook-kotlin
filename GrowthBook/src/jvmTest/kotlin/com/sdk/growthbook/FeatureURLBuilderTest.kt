package com.sdk.growthbook

import com.sdk.growthbook.features.FeatureURLBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureURLBuilderTest {

    @Test
    fun verifyCreateUrl() {
        val actual = FeatureURLBuilder().buildUrl("https://some.domain", "api_key")

        assertEquals("https://some.domain/api/features/api_key", actual)
    }

    @Test
    fun verifyCreateUrlWithDash() {
        val actual = FeatureURLBuilder()
            .buildUrl("https://some.domain/", "api_key_2")

        assertEquals("https://some.domain/api/features/api_key_2", actual)
    }
}
