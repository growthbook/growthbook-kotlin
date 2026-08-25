package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagTest {
    /**
     * Builds an SDK with a no-op network dispatcher and the given features
     * preloaded, so evaluation is fully deterministic and offline.
     */
    private fun sdkWith(features: Map<String, GBFeature>): GrowthBookSDK {
        val sdk = GBSDKBuilder(
            apiKey = "",
            apiHost = "",
            networkDispatcher = MockNetworkDispatcher(),
            attributes = emptyMap(),
            trackingCallback = { _, _ -> },
            // Off, so the suite never reads or writes the real per-user cache directory
            // (~/.growthbook on the JVM) — a stale payload there would otherwise override
            // setInitialFeatures and make these tests depend on the host machine.
            cachingEnabled = false,
        ).setInitialFeatures(features)
            .initialize()
        return sdk
    }

    @Test
    fun `value returns the feature value when present`() {
        val flag = Flag<Boolean>("flag", false)
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        val value = sdk.value(flag)
        assertTrue(value)
    }

    @Test
    fun `isOn returns the feature value when present`() {
        val flag = Flag<Boolean>("flag", false)
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        val value = sdk.isOn(flag)
        assertTrue(value)
    }

    @Test
    fun `isOn returns default when the feature value is missing`() {
        val flag = Flag<Boolean>("flag", false)
        val sdk = sdkWith(emptyMap())
        val value = sdk.isOn(flag)
        assertFalse(value)
    }

    @Test
    fun `value returns default when feature is missing`() {
        val flag = Flag<Boolean>("flag", false)
        val sdk = sdkWith(emptyMap())
        val value = sdk.value(flag)
        assertFalse(value)
    }

    @Test
    fun `value returns default when feature value is wrong type`() {
        val flag = Flag<Boolean>("flag", false)
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBString("feature"))))
        val value = sdk.value(flag)
        assertFalse(value)
    }

    @Test
    fun `value returns default when feature type mismatches`() {
        val flag = Flag("flag", 42)
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBString("dark"))))
        val value = sdk.value(flag)
        assertEquals(42, value)
    }

    @Test
    fun `value reads an Int flag stored as Float`() {
        val flag = Flag("ratio", 0)
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        val value = sdk.value(flag)
        assertEquals(3, value)
    }

    @Test
    fun `value throws error when Flag is unknown type`() {
        val flag = Flag("ratio", Any())
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        assertFailsWith<IllegalArgumentException> { sdk.value(flag) }
    }
}
