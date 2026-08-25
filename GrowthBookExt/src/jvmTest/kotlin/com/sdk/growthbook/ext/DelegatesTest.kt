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

class DelegatesTest {

    /**
     * Builds an SDK with a no-op network dispatcher and the given features
     * preloaded, so evaluation is fully deterministic and offline.
     */
    private fun sdkWith(features: Map<String, GBFeature>): GrowthBookSDK =
        GBSDKBuilder(
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

    @Test
    fun `boolean delegate returns true when feature is on`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        val flag by sdk.featureFlag("flag")
        assertTrue(flag)
    }

    @Test
    fun `boolean delegate returns false when feature is off`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        val flag by sdk.featureFlag("flag")
        assertFalse(flag)
    }

    @Test
    fun `boolean delegate returns false when feature is unknown`() {
        val sdk = sdkWith(emptyMap())
        val flag by sdk.featureFlag("flag")
        assertFalse(flag)
    }

    @Test
    fun `fallback delegate returns real value for known feature ignoring FAIL_OPEN`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        val flag by sdk.featureFlag("flag", FallbackStrategy.FAIL_OPEN)
        assertFalse(flag)
    }

    @Test
    fun `fallback delegate returns true for unknown feature with FAIL_OPEN`() {
        val sdk = sdkWith(emptyMap())
        val flag by sdk.featureFlag("flag", FallbackStrategy.FAIL_OPEN)
        assertTrue(flag)
    }

    @Test
    fun `fallback delegate returns false for unknown feature with FAIL_CLOSED`() {
        val sdk = sdkWith(emptyMap())
        val flag by sdk.featureFlag("flag", FallbackStrategy.FAIL_CLOSED)
        assertFalse(flag)
    }


    @Test
    fun `boolean flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        val flag by sdk.featureFlag(Flag("flag", false))
        assertTrue(flag)
    }

    @Test
    fun `boolean flag delegate returns default when missing`() {
        val sdk = sdkWith(emptyMap())
        val flag by sdk.featureFlag(Flag("flag", true))
        assertTrue(flag)
    }

    @Test
    fun `boolean flag delegate returns default when value is wrong type`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBString("nope"))))
        val flag by sdk.featureFlag(Flag("flag", true))
        assertTrue(flag)
    }

    @Test
    fun `string flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        val theme by sdk.featureFlag(Flag("theme", "light"))
        assertEquals("dark", theme)
    }

    @Test
    fun `string flag delegate returns default when missing`() {
        val sdk = sdkWith(emptyMap())
        val theme by sdk.featureFlag(Flag("theme", "light"))
        assertEquals("light", theme)
    }

    @Test
    fun `int flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("max" to GBFeature(GBNumber(42))))
        val max by sdk.featureFlag(Flag("max", 0))
        assertEquals(42, max)
    }

    @Test
    fun `long flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("threshold" to GBFeature(GBNumber(42L))))
        val threshold by sdk.featureFlag(Flag("threshold", 0L))
        assertEquals(42L, threshold)
    }

    @Test
    fun `float flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        val ratio by sdk.featureFlag(Flag("ratio", 0f))
        assertEquals(3.14f, ratio)
    }

    @Test
    fun `double flag delegate returns value when present`() {
        val sdk = sdkWith(mapOf("rate" to GBFeature(GBNumber(2.5))))
        val rate by sdk.featureFlag(Flag("rate", 0.0))
        assertEquals(2.5, rate)
    }

    @Test
    fun `int flag delegate reads a value stored as Float`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        val ratio by sdk.featureFlag(Flag("ratio", 0))
        assertEquals(3, ratio)
    }

    @Test
    fun `flag delegate throws for unsupported type`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        assertFailsWith<IllegalArgumentException> {
            val ratio by sdk.featureFlag(Flag("ratio", Any()))
            ratio // read triggers evaluation
        }
    }

    @Test
    fun `long flag delegate returns default when missing`() {
        val sdk = sdkWith(emptyMap())
        val threshold by sdk.featureFlag(Flag("threshold", 7L))
        assertEquals(7L, threshold)
    }

    @Test
    fun `float flag delegate returns default when missing`() {
        val sdk = sdkWith(emptyMap())
        val ratio by sdk.featureFlag(Flag("ratio", 1.5f))
        assertEquals(1.5f, ratio)
    }

    @Test
    fun `double flag delegate returns default when missing`() {
        val sdk = sdkWith(emptyMap())
        val rate by sdk.featureFlag(Flag("rate", 2.5))
        assertEquals(2.5, rate)
    }

    @Test
    fun `boolean delegate re-evaluates on each read`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        val flag by sdk.featureFlag("flag")

        assertFalse(flag) // off initially

        // Change the evaluated state after the property is declared.
        sdk.setForcedFeatures(mapOf("flag" to GBBoolean(true)))
        assertTrue(flag) // same property, re-read reflects the new state
    }

    @Test
    fun `fallback delegate re-evaluates on each read`() {
        val sdk = sdkWith(emptyMap())
        val flag by sdk.featureFlag("flag", FallbackStrategy.FAIL_CLOSED)

        assertFalse(flag) // unknown → fail closed

        sdk.setForcedFeatures(mapOf("flag" to GBBoolean(true)))
        assertTrue(flag) // now known and on
    }

    @Test
    fun `typed flag delegate re-evaluates on each read`() {
        val sdk = sdkWith(mapOf("max" to GBFeature(GBNumber(1))))
        val max by sdk.featureFlag(Flag("max", 0))

        assertEquals(1, max)

        sdk.setForcedFeatures(mapOf("max" to GBNumber(2)))
        assertEquals(2, max) // same property, re-read reflects the new state
    }
}
