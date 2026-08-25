package com.sdk.growthbook.ext

import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfigDslTest {

    @Test
    fun `growthBook builds a usable SDK from required config`() {
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
        }
        assertFalse(sdk.isEnabled("nope"))
    }

    @Test
    fun `growthBook throws when apiKey is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiHost = "host"
                networkDispatcher = MockNetworkDispatcher()
            }
        }
        assertTrue(exception.message!!.contains("apiKey"))
    }

    @Test
    fun `growthBook throws when apiHost is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiKey = "key"
                networkDispatcher = MockNetworkDispatcher()
            }
        }
        assertTrue(exception.message!!.contains("apiHost"))
    }

    @Test
    fun `growthBook throws when networkDispatcher is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            growthBook {
                apiKey = "key"
                apiHost = "host"
            }
        }
        assertTrue(exception.message!!.contains("networkDispatcher"))
    }

    @Test
    fun `growthBook wires initialFeatures and attributes into the SDK`() {
        val feature = GBFeature(
            defaultValue = GBString("generic"),
            rules = listOf(
                GBFeatureRule(
                    condition = Json.parseToJsonElement("""{"country":"UA"}"""),
                    force = GBString("ua-only"),
                ),
            ),
        )
        val sdk = growthBook {
            apiKey = "key"
            apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            // Off, so a stale payload in the real cache directory (~/.growthbook on the JVM)
            // cannot override setInitialFeatures and make this assertion host-dependent.
            cachingEnabled = false
            attributes { "country" to "UA" }
            initialFeatures = mapOf("promo" to feature)
        }

        assertEquals("ua-only", sdk.getStringOrNull("promo"))
    }

    @Test
    fun `growthBook wires the enabled flag into the SDK`() {
        val experiment = GBFeature(
            defaultValue = GBString("control"),
            rules = listOf(
                GBFeatureRule(
                    hashAttribute = "id",
                    coverage = 1f,
                    variations = listOf(GBString("A"), GBString("B"))
                )
            )
        )

        fun buildSdk(isEnabled: Boolean) = growthBook {
            apiKey = "key"; apiHost = "host"
            networkDispatcher = MockNetworkDispatcher()
            cachingEnabled = false
            enabled = isEnabled
            attributes { "id" to "user-123" }
            initialFeatures = mapOf("exp" to experiment)
        }

        assertEquals("control", buildSdk(false).getStringOrNull("exp"))

        assertNotEquals("control", buildSdk(true).getStringOrNull("exp"))
    }
}