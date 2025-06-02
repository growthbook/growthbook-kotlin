package com.sdk.growthbook.tests

import kotlin.test.Test
import kotlin.random.Random
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.toGbBoolean
import com.sdk.growthbook.model.toGbNumber
import kotlin.test.assertEquals

class GBExperimentSwitcherTests {
    private val featureKey = "feature-576"

    @Test
    fun `when enabled, the experiment will be run`() {
        val testInstance = createInstanceForSwitcherTest(
            enabled = true,
        )
        val featureResult = testInstance.feature(featureKey)
        assertEquals(GBFeatureSource.experiment, featureResult.source)
    }

    @Test
    fun `if disabled, the default value is the source`() {
        val testInstance = createInstanceForSwitcherTest(
            enabled = false,
        )
        val featureResult = testInstance.feature(featureKey)
        assertEquals(GBFeatureSource.defaultValue, featureResult.source)
    }

    private fun createInstanceForSwitcherTest(enabled: Boolean): GrowthBookSDK {
        val hashAttribute = "user_id"
        return GrowthBookSDK(
            gbContext = GBContext(
                apiKey = "some_api_key",
                enabled = enabled,
                attributes = mapOf(
                    hashAttribute to Random.nextInt().toGbNumber()
                ),
                forcedVariations = emptyMap(),
                encryptionKey = null,
                qaMode = false,
                hostURL = "https://test-host.com",
                trackingCallback = { _, _ -> },

                ),
            features = mapOf(
                featureKey to GBFeature(
                    defaultValue = GBBoolean(false),
                    rules = listOf(
                        GBFeatureRule(
                            hashAttribute = hashAttribute,
                            variations = listOf(false, true)
                                .map { it.toGbBoolean() }
                        )
                    )
                )
            ),
            cachingEnabled = false,
            networkDispatcher = MockNetworkClient(
                "", null
            ),
            refreshHandler = { _, _ -> }
        )
    }
}
