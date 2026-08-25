package com.sdk.growthbook.test

import com.sdk.growthbook.featureValue
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBVariationMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    /**
     * The fake must agree with the core SDK's `GBFeatureEvaluator`, which mirrors the
     * reference (TypeScript) SDK's `off = !value`. Zero is falsy in JS regardless of how
     * it is spelled, so every numeric representation has to be covered — `value == 0`
     * on a boxed [Number] would silently only match Byte/Short/Int.
     */
    @Test
    fun numericZeroIsFalsyInEveryRepresentation() {
        val gb = FakeGrowthBook()
            .setValue("int-zero", 0)
            .setValue("long-zero", 0L)
            .setValue("float-zero", 0.0f)
            .setValue("double-zero", 0.0)
            .setValue("negative-zero", -0.0)
            .setValue("nan", Double.NaN)

        assertFalse(gb.isOn("int-zero"))
        assertFalse(gb.isOn("long-zero"))
        assertFalse(gb.isOn("float-zero"))
        assertFalse(gb.isOn("double-zero"))
        assertFalse(gb.isOn("negative-zero"))
        assertFalse(gb.isOn("nan"))
    }

    @Test
    fun nonZeroNumbersAndInfinityAreTruthy() {
        val gb = FakeGrowthBook()
            .setValue("fraction", 0.1)
            .setValue("infinity", Double.POSITIVE_INFINITY)

        assertTrue(gb.isOn("fraction"))
        assertTrue(gb.isOn("infinity"))
    }

    @Test
    fun unknownValueIsFalsy() {
        val gb = FakeGrowthBook().setValue("unresolvable", GBValue.Unknown)

        assertFalse(gb.isOn("unresolvable"))
    }

    /** `"0"`, `"false"` and empty containers are all truthy in JS, so they stay on. */
    @Test
    fun stringZeroAndEmptyContainersAreTruthy() {
        val gb = FakeGrowthBook()
            .setValue("string-zero", "0")
            .setValue("string-false", "false")
            .setValue("empty-array", GBArray(emptyList()))
            .setValue("empty-object", GBJson(emptyMap()))

        assertTrue(gb.isOn("string-zero"))
        assertTrue(gb.isOn("string-false"))
        assertTrue(gb.isOn("empty-array"))
        assertTrue(gb.isOn("empty-object"))
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

    /**
     * `GBExperimentResult.key` is the *variation* key, not the experiment key — the real
     * `GBExperimentEvaluator.getExperimentResult` uses `meta[index].key ?: "$index"`.
     */
    @Test
    fun runReportsTheVariationKeyNotTheExperimentKey() {
        val experiment = GBExperiment(
            key = "exp",
            variations = listOf(GBString("control"), GBString("treatment")),
        )

        assertEquals("0", FakeGrowthBook().run(experiment).key)
        assertEquals("1", FakeGrowthBook().setForcedVariation("exp", 1).run(experiment).key)
    }

    @Test
    fun runUsesVariationMetaWhenPresent() {
        val experiment = GBExperiment(
            key = "exp",
            variations = listOf(GBString("control"), GBString("treatment")),
            meta = arrayListOf(
                GBVariationMeta(key = "ctl", name = "Control"),
                GBVariationMeta(key = "trt", name = "Treatment"),
            ),
        )

        val result = FakeGrowthBook().setForcedVariation("exp", 1).run(experiment)
        assertEquals("trt", result.key)
        assertEquals("Treatment", result.name)
    }

    /**
     * A forced index outside the variations range must fall back to the baseline and mark the
     * user as not in the experiment, exactly as the real SDK does — otherwise a typo'd index
     * "passes" as an in-experiment result that production can never produce.
     */
    @Test
    fun outOfRangeForcedVariationFallsBackToBaseline() {
        val experiment = GBExperiment(
            key = "exp",
            variations = listOf(GBString("control"), GBString("treatment")),
        )

        for (badIndex in listOf(5, -1)) {
            val result = FakeGrowthBook().setForcedVariation("exp", badIndex).run(experiment)

            assertFalse(result.inExperiment, "index $badIndex must not put the user in the experiment")
            assertEquals(0, result.variationId, "index $badIndex must fall back to the baseline")
            assertEquals(GBString("control"), result.value)
            assertEquals("0", result.key)
        }
    }

    @Test
    fun experimentWithoutVariationsYieldsUnknownValue() {
        val experiment = GBExperiment(key = "exp", variations = emptyList())

        val result = FakeGrowthBook().setForcedVariation("exp", 0).run(experiment)

        assertFalse(result.inExperiment)
        assertEquals(0, result.variationId)
        assertEquals(GBValue.Unknown, result.value)
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

    // --- reported source ------------------------------------------------------------

    /**
     * `source` must reflect where the value came from, because code and observability hooks
     * branch on it — `setFeatureUsageCallback` and `GrowthBookPlugin` both receive it.
     */
    @Test
    fun valuesSetFromCodeReportOverrideSource() {
        val gb = FakeGrowthBook().setValue("welcome-copy", "Hello").enable("new-home")

        assertEquals(GBFeatureSource.override, gb.feature("welcome-copy").source)
        assertEquals(GBFeatureSource.override, gb.feature("new-home").source)
    }

    @Test
    fun seededAndLoadedValuesReportDefaultValueSource() {
        val seeded = FakeGrowthBook().setFeatures(mapOf("new-home" to GBString("Hi")))
        assertEquals(GBFeatureSource.defaultValue, seeded.feature("new-home").source)

        val loaded = FakeGrowthBook.fromFeaturesJson("""{"new-home": {"defaultValue": true}}""")
        assertEquals(GBFeatureSource.defaultValue, loaded.feature("new-home").source)
    }

    @Test
    fun copyPreservesTheReportedSource() {
        val base = FakeGrowthBook()
            .setFeatures(mapOf("seeded" to GBString("Hi")))
            .setValue("forced", "There")

        val forked = base.copy()

        assertEquals(GBFeatureSource.defaultValue, forked.feature("seeded").source)
        assertEquals(GBFeatureSource.override, forked.feature("forced").source)
    }

    // --- fromFeaturesJson error handling ---------------------------------------------

    @Test
    fun fromFeaturesJsonRejectsEncryptedPayloads() {
        val payload = """{"status": 200, "encryptedFeatures": "abc.def"}"""

        val error = assertFailsWith<IllegalArgumentException> {
            FakeGrowthBook.fromFeaturesJson(payload)
        }
        assertTrue(
            error.message.orEmpty().contains("encrypted"),
            "message should name the cause, was: ${error.message}",
        )
    }

    @Test
    fun fromFeaturesJsonRejectsNonObjectJson() {
        val error = assertFailsWith<IllegalArgumentException> {
            FakeGrowthBook.fromFeaturesJson("""[1, 2]""")
        }
        // Asserting on the message, not just the type: kotlinx's own `.jsonObject` accessor
        // already threw IllegalArgumentException here — what this guards is that the caller is
        // told what shape was expected.
        assertTrue(
            error.message.orEmpty().contains("fromFeaturesJson"),
            "message should explain the expected shape, was: ${error.message}",
        )
    }

    @Test
    fun fromFeaturesJsonRejectsJsonWithoutFeatures() {
        val error = assertFailsWith<IllegalArgumentException> {
            FakeGrowthBook.fromFeaturesJson("""{"status": 200, "dateUpdated": "yesterday"}""")
        }
        assertTrue(
            error.message.orEmpty().contains("status"),
            "message should list the keys it did find, was: ${error.message}",
        )
    }

    @Test
    fun fromFeaturesJsonNamesTheOffendingFeature() {
        val payload = """{"features": {"broken": [1, 2]}}"""

        val error = assertFailsWith<IllegalArgumentException> {
            FakeGrowthBook.fromFeaturesJson(payload)
        }
        assertTrue(
            error.message.orEmpty().contains("broken"),
            "message should name the feature, was: ${error.message}",
        )
    }

    /**
     * The decoder ignores unknown keys, so a feature definition carrying extra fields must be
     * accepted — the shape check must not be stricter than the decoder it guards.
     */
    @Test
    fun fromFeaturesJsonAcceptsFeaturesWithExtraFields() {
        val payload = """
            {
              "new-home": {
                "id": "new-home",
                "description": "the new home screen",
                "defaultValue": true
              }
            }
        """.trimIndent()

        assertTrue(FakeGrowthBook.fromFeaturesJson(payload).isOn("new-home"))
    }

    /**
     * A bare map may legitimately contain a flag named `features`. Keying off the presence of
     * that name alone would load the flag's *body* as the feature map — silently, with no error.
     */
    @Test
    fun fromFeaturesJsonHandlesAFlagNamedFeatures() {
        val payload = """
            {
              "features": { "defaultValue": "flag-value" },
              "promo-banner": { "defaultValue": true }
            }
        """.trimIndent()

        val gb = FakeGrowthBook.fromFeaturesJson(payload)

        assertEquals("flag-value", gb.featureValue<String>("features"))
        assertTrue(gb.isOn("promo-banner"))
    }
}
