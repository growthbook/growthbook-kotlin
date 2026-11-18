package com.sdk.growthbook.ext

import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.test.FakeGrowthBook
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards that every helper in this module is usable against *any* [IGrowthBookSDK], not just the
 * concrete `GrowthBookSDK` — the point of taking the interface as the extension receiver.
 *
 * The other suites cover behaviour against the real SDK, where rules, targeting and `source` are
 * genuinely evaluated; [FakeGrowthBook] deliberately evaluates none of that. So these tests assert
 * the interop itself rather than re-testing semantics: the receiver is declared as
 * [IGrowthBookSDK] throughout, which means a regression back to a concrete receiver type stops
 * compiling here instead of surfacing in a consumer's test suite.
 */
class FakeGrowthBookInteropTest {

    @Test
    fun `Extensions helpers work through the interface`() {
        val gb: IGrowthBookSDK = FakeGrowthBook()
            .enable("new-home")
            .disable("promo-banner")
            .setValue("welcome-copy", "Hello")
            .setValue("max-items", 25)

        assertTrue(gb.isEnabled("new-home"))
        assertTrue(gb.isDisabled("promo-banner"))
        assertFalse(gb.isEnabled("never-configured"))

        assertEquals("Hello", gb.getString("welcome-copy", "fallback"))
        assertEquals("fallback", gb.getString("never-configured", "fallback"))
        assertEquals(25, gb.getInt("max-items", 0))
        assertEquals(25L, gb.getLong("max-items", 0))
        assertEquals(25.0, gb.getDouble("max-items", 0.0))
        assertTrue(gb.getBoolean("new-home", false))
    }

    @Test
    fun `isFeatureKnown distinguishes a configured-but-off feature from a missing one`() {
        val gb: IGrowthBookSDK = FakeGrowthBook().disable("promo-banner")

        assertTrue(gb.isFeatureKnown("promo-banner"))
        assertFalse(gb.isFeatureKnown("never-configured"))
    }

    @Test
    fun `isFeatureKnown does not count as a feature read`() {
        // The short-circuit in isFeatureKnown relies on getFeatures() being populated. Were the
        // fake to inherit the interface's empty default, the check would fall through to
        // feature(id) and register an interaction that the caller never asked for.
        val fake = FakeGrowthBook().disable("promo-banner")
        val gb: IGrowthBookSDK = fake

        assertTrue(gb.isFeatureKnown("promo-banner"))

        assertEquals(emptyList(), fake.queriedFeatures())
    }

    @Test
    fun `fallback strategies apply only to unknown features`() {
        val gb: IGrowthBookSDK = FakeGrowthBook().disable("promo-banner")

        // Configured and off: the real value wins over either strategy.
        assertFalse(gb.isEnabled("promo-banner", FallbackStrategy.FAIL_OPEN))
        assertFalse(gb.isEnabled("promo-banner", FallbackStrategy.FAIL_CLOSED))

        assertTrue(gb.isEnabled("never-configured", FallbackStrategy.FAIL_OPEN))
        assertFalse(gb.isEnabled("never-configured", FallbackStrategy.FAIL_CLOSED))
    }

    @Test
    fun `Flag helpers work through the interface`() {
        val enabled = Flag("new-home", false)
        val copy = Flag("welcome-copy", "Default")
        val gb: IGrowthBookSDK = FakeGrowthBook().enable("new-home")

        assertTrue(gb.isOn(enabled))
        assertTrue(gb.value(enabled))
        assertEquals("Default", gb.value(copy))
    }

    @Test
    fun `property delegates work through the interface`() {
        val gb: IGrowthBookSDK = FakeGrowthBook()
            .enable("new-home")
            .setValue("max-items", 25)

        val newHome by gb.featureFlag("new-home")
        val missing by gb.featureFlag("never-configured", FallbackStrategy.FAIL_OPEN)
        val maxItems by gb.featureFlag(Flag("max-items", 10))

        assertTrue(newHome)
        assertTrue(missing)
        assertEquals(25, maxItems)
    }

    @Test
    fun `attributes DSL works through the interface`() {
        val fake = FakeGrowthBook()
        val gb: IGrowthBookSDK = fake

        gb.setAttributes {
            "id" to "user-123"
            "premium" to true
        }

        assertEquals(GBString("user-123"), fake.attributes()["id"])

        runBlocking {
            gb.setAttributesSync {
                "id" to "user-456"
            }
        }

        assertEquals(GBString("user-456"), fake.attributes()["id"])
    }
}
