package com.sdk.growthbook.test

import com.sdk.growthbook.featureValue
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeGrowthBookTest {

    @Test
    fun enabledFeatureIsOn() {
        val gb = FakeGrowthBook().enable("new-home")

        assertTrue(gb.isOn("new-home"))
    }

    @Test
    fun disabledFeatureIsOff() {
        val gb = FakeGrowthBook().disable("new-home")

        assertFalse(gb.isOn("new-home"))
    }

    @Test
    fun unknownFeatureIsOffAndUnknownSource() {
        val gb = FakeGrowthBook()

        val result = gb.feature("checkout-v2")
        assertFalse(result.on)
        assertNull(result.gbValue)
        assertEquals(GBFeatureSource.unknownFeature, result.source)
    }

    @Test
    fun typedValuesAreReadableViaFeatureValueExtension() {
        val gb = FakeGrowthBook()
            .setValue("welcome-copy", "Hello")
            .setValue("max-items", 25)

        assertEquals("Hello", gb.featureValue<String>("welcome-copy"))
        assertEquals(25, gb.featureValue<Number>("max-items")?.toInt())
    }

    @Test
    fun numericZeroIsFalsyLikeTheRealSdk() {
        val gb = FakeGrowthBook().setValue("threshold", 0)

        assertFalse(gb.isOn("threshold"))
    }

    @Test
    fun emptyStringIsFalsyLikeTheEtalon() {
        val gb = FakeGrowthBook().setValue("welcome-copy", "")

        assertFalse(gb.isOn("welcome-copy"))
    }

    @Test
    fun jsonNullIsFalsyLikeTheEtalon() {
        val gb = FakeGrowthBook().setValue("maybe-config", GBNull)

        assertFalse(gb.isOn("maybe-config"))
    }

    @Test
    fun clearMakesFeatureUnknownAgain() {
        val gb = FakeGrowthBook().enable("new-home")
        assertTrue(gb.isOn("new-home"))

        gb.clear("new-home")
        assertFalse(gb.isOn("new-home"))
    }

    @Test
    fun setFeaturesBulkSeedsValues() {
        val gb = FakeGrowthBook().setFeatures(
            mapOf(
                "new-home" to GBString("Hello"),
                "promo-banner" to GBNull,
            )
        )

        assertEquals("Hello", gb.featureValue<String>("new-home"))
        assertFalse(gb.isOn("promo-banner"))
    }

    @Test
    fun copyForksWithoutMutatingOriginal() {
        val base = FakeGrowthBook().enable("new-home")

        val forked = base.copy().disable("new-home").enable("checkout-v2")

        assertTrue(base.isOn("new-home"))
        assertFalse(base.isOn("checkout-v2"))
        assertFalse(forked.isOn("new-home"))
        assertTrue(forked.isOn("checkout-v2"))
    }

    @Test
    fun runReturnsControlWhenNothingForced() {
        val gb = FakeGrowthBook()
        val experiment = GBExperiment(
            key = "exp",
            variations = listOf(GBString("control"), GBString("treatment")),
        )

        val result = gb.run(experiment)
        assertFalse(result.inExperiment)
        assertEquals(0, result.variationId)
        assertEquals(GBString("control"), result.value)
    }

    @Test
    fun forcedVariationPutsUserInExperiment() {
        val gb = FakeGrowthBook().setForcedVariation("exp", 1)
        val experiment = GBExperiment(
            key = "exp",
            variations = listOf(GBString("control"), GBString("treatment")),
        )

        val result = gb.run(experiment)
        assertTrue(result.inExperiment)
        assertEquals(1, result.variationId)
        assertEquals(GBString("treatment"), result.value)
    }

    @Test
    fun tracksQueriedFeatures() {
        val gb = FakeGrowthBook().enable("new-home")

        gb.isOn("new-home")
        gb.isOn("checkout-v2")

        assertTrue(gb.wasQueried("new-home"))
        assertTrue(gb.wasQueried("checkout-v2"))
        assertFalse(gb.wasQueried("never-touched"))
        assertEquals(listOf("new-home", "checkout-v2"), gb.queriedFeatures())
    }

    @Test
    fun fromFeaturesJsonLoadsDefaultValues() {
        val payload = """
            {
              "features": {
                "new-home": { "defaultValue": true },
                "welcome-copy": { "defaultValue": "Hello" },
                "max-items": { "defaultValue": 25 }
              }
            }
        """.trimIndent()

        val gb = FakeGrowthBook.fromFeaturesJson(payload)

        assertTrue(gb.isOn("new-home"))
        assertEquals("Hello", gb.featureValue<String>("welcome-copy"))
        assertEquals(25, gb.featureValue<Number>("max-items")?.toInt())
    }

    @Test
    fun fromFeaturesJsonAcceptsBareFeaturesObject() {
        val payload = """{ "new-home": { "defaultValue": true } }"""

        val gb = FakeGrowthBook.fromFeaturesJson(payload)

        assertTrue(gb.isOn("new-home"))
    }
}
