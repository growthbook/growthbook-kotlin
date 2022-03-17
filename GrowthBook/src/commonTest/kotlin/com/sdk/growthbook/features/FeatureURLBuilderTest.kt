package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBContext
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureURLBuilderTest {

	@Test
	fun verifyCreateUrl() {
		val actual = FeatureURLBuilder()
				.buildUrl(gBContext = buildGbContext("https://some.domain", "api_key"))

		assertEquals("https://some.domain/api/features/api_key", actual)
	}

	@Test
	fun verifyCreateUrlWithDash() {
		val actual = FeatureURLBuilder()
				.buildUrl(gBContext = buildGbContext("https://some.domain/", "api_key_2"))

		assertEquals("https://some.domain/api/features/api_key_2", actual)
	}

	private fun buildGbContext(hostUrl: String, apiKey: String): GBContext {
		return GBContext(
				apiKey = apiKey,
				hostURL = hostUrl,
				enabled = true,
				attributes = emptyMap(),
				forcedVariations = emptyMap(),
				qaMode = true,
				trackingCallback = { _, _ -> })
	}

}