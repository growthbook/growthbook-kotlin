package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AttributesDslTest {

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
        ).setInitialFeatures(features).initialize()

    @Test
    fun `buildAttributes converts primitives, list and nested map `() {
        val attrs = buildAttributes {
            "null" to null
            "number" to 1
            "bool" to true
            "arr" to listOf(1, 2, 3)
            "map" to obj {
                "name" to "value"
            }
        }
        assertEquals(GBNull, attrs["null"])
        assertEquals(GBNumber(1), attrs["number"])
        assertEquals(GBBoolean(true), attrs["bool"])
        assertEquals(
            listOf(GBNumber(1), GBNumber(2), GBNumber(3)),
            (attrs["arr"] as GBArray).toList()
        )
        assertEquals(
            GBJson(mapOf("name" to GBString("value"))),
            attrs["map"]
        )
    }

    @Test
    fun `buildAttributes passes through existing GBValue unchanged`() {
        val ready = GBString("ready")

        val attrs = buildAttributes {
            "x" to ready
        }

        assertEquals(GBString("ready"), attrs["x"])
        assertSame(ready, attrs["x"])
    }

    @Test
    fun `buildAttributes throws when value is unknown type`() {
        assertFailsWith<IllegalArgumentException> {
            buildAttributes { "x" to Any() }
        }
    }

    @Test
    fun `buildAttributes throws when nested map key is not String`() {
        assertFailsWith<IllegalArgumentException> {
            buildAttributes {
                "map" to mapOf(1 to "value")
            }
        }
    }

    @Test
    fun `buildAttributes accepts a String-keyed map built outside the block`() {
        // Inside the block `"city" to "Kyiv"` would resolve to the builder's own `to`
        // (a member of the DSL receiver shadows kotlin.to), so a String-keyed map has to be
        // built outside it — or nested with obj { }, as the test above does.
        val address = mapOf("city" to "Kyiv", "zip" to 1001)

        val attrs = buildAttributes {
            "address" to address
        }

        assertEquals(
            GBJson(mapOf("city" to GBString("Kyiv"), "zip" to GBNumber(1001))),
            attrs["address"]
        )
    }

    @Test
    fun `buildAttributes preserves insertion order`() {
        val attrs = buildAttributes {
            "a" to 1
            "b" to 2
            "c" to 3
        }
        assertEquals(listOf("a", "b", "c"), attrs.keys.toList())
    }

    @Test
    fun `buildAttributes overwrites duplicate key with last value`() {
        val attrs = buildAttributes {
            "a" to 1
            "a" to 2
        }
        assertEquals(GBNumber(2), attrs["a"])
    }

    @Test
    fun `setAttributes DSL updates live targeting`() {
        val feature = GBFeature(
            defaultValue = GBString("generic"),
            rules = listOf(
                GBFeatureRule(
                    condition = Json.parseToJsonElement("""{"country":"UA"}"""),
                    force = GBString("ua-only"),
                ),
            ),
        )
        val sdk = sdkWith(mapOf("promo" to feature))

        assertEquals("generic", sdk.getStringOrNull("promo"))

        sdk.setAttributes { "country" to "UA" }

        assertEquals("ua-only", sdk.getStringOrNull("promo"))
    }
}
