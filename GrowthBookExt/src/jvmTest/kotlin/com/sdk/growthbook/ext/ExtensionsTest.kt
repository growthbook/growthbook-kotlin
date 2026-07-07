package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionsTest {

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
        ).setInitialFeatures(features)
            .initialize()
        return sdk
    }

    @Test
    fun `isEnabled returns true when features is on`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        assertTrue(sdk.isEnabled("flag"))
    }

    @Test
    fun `isEnabled returns false when features is off`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        assertFalse(sdk.isEnabled("flag"))
    }

    @Test
    fun `isEnabled returns false when features is missing`() {
        val sdk = sdkWith(emptyMap())
        assertFalse(sdk.isEnabled("missing"))
    }

    @Test
    fun `isDisabled returns true when features is off`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        assertTrue(sdk.isDisabled("flag"))
    }

    @Test
    fun `isDisabled returns false when features is on`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(true))))
        assertFalse(sdk.isDisabled("flag"))
    }

    @Test
    fun `isDisabled returns true when features is missing`() {
        val sdk = sdkWith(emptyMap())
        assertTrue(sdk.isDisabled("missing"))
    }

    @Test
    fun `isFeatureKnown is true for a loaded feature`() {
        val sdk = sdkWith(mapOf("flag" to GBFeature(GBBoolean(false))))
        assertTrue(sdk.isFeatureKnown("flag"))
    }

    @Test
    fun `isFeatureKnown is false for a missing feature`() {
        val sdk = sdkWith(emptyMap())
        assertFalse(sdk.isFeatureKnown("missing"))
    }

    @Test
    fun `getString returns feature value`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals("dark", sdk.getString("theme", "light"))
    }

    @Test
    fun `getString returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals("light", sdk.getString("theme", "light"))
    }

    @Test
    fun `getStringOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals("dark", sdk.getStringOrNull("theme"))
    }

    @Test
    fun `getStringOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getStringOrNull("theme"))
    }

    @Test
    fun `getStringOrElse returns feature value and skips default lambda`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        var called = false
        val actual = sdk.getStringOrElse(
            id = "theme",
            default = {
                called = true
                "light"
            }
        )
        assertEquals("dark", actual)
        assertFalse(called)
    }

    @Test
    fun `getStringOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getStringOrElse(
            id = "theme",
            default = {
                called = true
                "light"
            }
        )
        assertEquals("light", actual)
        assertTrue(called)
    }

    @Test
    fun `getInt returns feature value`() {
        val sdk = sdkWith(mapOf("max" to GBFeature(GBNumber(42))))
        assertEquals(42, sdk.getInt("max", 0))
    }

    @Test
    fun `getInt returns default when feature is not numeric`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals(-1, sdk.getInt("theme", -1))
    }

    @Test
    fun `getInt returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(7, sdk.getInt("max", 7))
    }

    @Test
    fun `getIntOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("max" to GBFeature(GBNumber(42))))
        assertEquals(42, sdk.getIntOrNull("max"))
    }

    @Test
    fun `getIntOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getIntOrNull("max"))
    }

    @Test
    fun `getIntOrElse returns feature value and skips default lambda`() {
        val sdk = sdkWith(mapOf("max" to GBFeature(GBNumber(42))))
        var called = false
        val actual = sdk.getIntOrElse(
            id = "max",
            default = {
                called = true
                0
            })
        assertEquals(42, actual)
        assertFalse(called)
    }

    @Test
    fun `getIntOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getIntOrElse(
            id = "max",
            default = {
                called = true
                0
            })
        assertEquals(0, actual)
        assertTrue(called)
    }

    @Test
    fun `getLong returns feature value`() {
        val sdk = sdkWith(mapOf("ts" to GBFeature(GBNumber(10_000_000_000L))))
        assertEquals(10_000_000_000L, sdk.getLong("ts", 0L))
    }

    @Test
    fun `getLong returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(0L, sdk.getLong("ts", 0L))
    }

    @Test
    fun `getLong returns default when feature is not numeric`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals(-1L, sdk.getLong("theme", -1L))
    }

    @Test
    fun `getLongOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("ts" to GBFeature(GBNumber(10_000_000_000L))))
        assertEquals(10_000_000_000L, sdk.getLongOrNull("ts"))
    }

    @Test
    fun `getLongOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getLongOrNull("ts"))
    }

    @Test
    fun `getLongOrElse returns feature value and skips default lambda`() {
        val sdk = sdkWith(mapOf("ts" to GBFeature(GBNumber(10_000_000_000L))))
        var called = false
        val actual = sdk.getLongOrElse("ts", { 0L })
        assertEquals(10_000_000_000L, actual)
        assertFalse(called)
    }

    @Test
    fun `getLongOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getLongOrElse("ts", {
            called = true
            0L
        })
        assertEquals(0L, actual)
        assertTrue(called)
    }

    @Test
    fun `getFloat returns feature value`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(1.5f))))
        assertEquals(1.5f, sdk.getFloat("ratio", default = 0f))
    }

    @Test
    fun `getFloat returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(0f, sdk.getFloat("ratio", default = 0f))
    }

    @Test
    fun `getFloat returns default when feature is not numeric`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals(-1f, sdk.getFloat("theme", -1f))
    }

    @Test
    fun `getFloatOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(1.5f))))
        assertEquals(1.5f, sdk.getFloatOrNull("ratio"))
    }

    @Test
    fun `getFloatOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getFloatOrNull("ratio"))
    }

    @Test
    fun `getFloatOrElse returns feature value and skips default lambda`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(1.5f))))
        var called = false
        val actual = sdk.getFloatOrElse("ratio", {
            called = true
            0f
        })
        assertEquals(1.5f, actual)
        assertFalse(called)
    }

    @Test
    fun `getFloatOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getFloatOrElse("ratio", {
            called = true
            0f
        })
        assertEquals(0f, actual)
        assertTrue(called)
    }

    @Test
    fun `getBoolean returns feature value`() {
        val sdk = sdkWith(mapOf("beta" to GBFeature(GBBoolean(true))))
        assertTrue(sdk.getBoolean("beta", default = false))
    }

    @Test
    fun `getBoolean returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertTrue(sdk.getBoolean("beta", default = true))
    }

    @Test
    fun `getBoolean returns default when feature is not boolean`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals(false, sdk.getBoolean("theme", false))
    }

    @Test
    fun `getBooleanOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("beta" to GBFeature(GBBoolean(true))))
        assertEquals(true, sdk.getBooleanOrNull("beta"))
    }

    @Test
    fun `getBooleanOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertNull(sdk.getBooleanOrNull("beta"))
    }

    @Test
    fun `getBooleanOrElse returns feature value and skips lambda default`() {
        val sdk = sdkWith(mapOf("beta" to GBFeature(GBBoolean(true))))
        var called = false
        val actual = sdk.getBooleanOrElse("beta", {
            called = true
            false
        })
        assertEquals(true, actual)
        assertFalse(called)
    }

    @Test
    fun `getBooleanOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getBooleanOrElse("beta", {
            called = true
            false
        })
        assertEquals(false, actual)
        assertTrue(called)
    }

    @Test
    fun `getDouble returns feature value`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14))))
        assertEquals(3.14, sdk.getDouble("ratio", 0.0))
    }

    @Test
    fun `getDouble returns default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(1.5, sdk.getDouble("ratio", default = 1.5), 0.001)
    }

    @Test
    fun `getDouble returns default when feature is not numeric`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertEquals(-1.0, sdk.getDouble("theme", -1.0))
    }

    @Test
    fun `getDoubleOrNull returns feature value`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14))))
        assertEquals(3.14, sdk.getDoubleOrNull("ratio"))
    }

    @Test
    fun `getDoubleOrNull returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getDoubleOrNull("ratio"))
    }

    @Test
    fun `getDoubleOrElse returns feature value`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14))))
        var called = false
        val actual = sdk.getDoubleOrElse("ratio", {
            called = true
            0.0
        })
        assertEquals(3.14, actual)
        assertFalse(called)
    }

    @Test
    fun `getDoubleOrElse computes default when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        var called = false
        val actual = sdk.getDoubleOrElse("ratio", {
            called = true
            0.0
        })
        assertEquals(0.0, actual)
        assertTrue(called)
    }

    // Guards the Int-vs-Long / Float-vs-Double cast pitfall: real JSON decimals
    // deserialize to Float, so getDouble must go through Number, not a strict cast.
    @Test
    fun `getDouble reads a value stored as Float`() {
        val sdk = sdkWith(mapOf("ratio" to GBFeature(GBNumber(3.14f))))
        assertEquals(3.14, sdk.getDouble("ratio", default = 0.0), 0.001)
    }

    @Test
    fun `getJson returns structured value`() {
        val sdk = sdkWith(
            mapOf("config" to GBFeature(GBJson(mapOf("mode" to GBString("dark")))))
        )
        val json = sdk.getJson("config")
        assertEquals(GBString("dark"), json?.get("mode"))
    }

    @Test
    fun `getJson returns null when feature is missing`() {
        val sdk = sdkWith(emptyMap())
        assertEquals(null, sdk.getJson("config"))
    }

    @Test
    fun `getJson returns null when feature is not json`() {
        val sdk = sdkWith(mapOf("theme" to GBFeature(GBString("dark"))))
        assertNull(sdk.getJson("theme"))
    }
}
