package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBFeatures
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Feature truthiness must match the reference (TypeScript) SDK, which derives
 * `on`/`off` straight from JS falsiness of the decoded value
 * (`packages/sdk-js/src/core.ts`: `on: !!value, off: !value`).
 *
 * The shared cross-SDK spec (`cases.json`) only ever exercises integer `0`, `null`
 * and `false`, so the remaining falsy values — `0.0`, `0L`, `-0.0`, `NaN` and the
 * empty string — need dedicated coverage here.
 */
class GBFeatureTruthinessTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private fun sdkWith(value: GBValue?): GrowthBookSDK {
        val features: GBFeatures = mapOf(FEATURE_KEY to GBFeature(defaultValue = value))
        return GBSDKBuilder(
            apiKey = "truthiness-key",
            apiHost = "https://host.com",
            attributes = emptyMap(),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        )
            .setInitialFeatures(features)
            .initialize()
    }

    private fun assertOff(value: GBValue?, hint: String) {
        val result = sdkWith(value).feature(FEATURE_KEY)
        assertFalse(result.on, "$hint should be off (on = false)")
        assertTrue(result.off, "$hint should be off (off = true)")
    }

    private fun assertOn(value: GBValue?, hint: String) {
        val result = sdkWith(value).feature(FEATURE_KEY)
        assertTrue(result.on, "$hint should be on (on = true)")
        assertFalse(result.off, "$hint should be on (off = false)")
    }

    // --- falsy: numeric zero in every representation -------------------------------

    @Test
    fun intZeroIsOff() = assertOff(GBNumber(0), "Int 0")

    @Test
    fun longZeroIsOff() = assertOff(GBNumber(0L), "Long 0")

    @Test
    fun floatZeroIsOff() = assertOff(GBNumber(0.0f), "Float 0.0")

    @Test
    fun doubleZeroIsOff() = assertOff(GBNumber(0.0), "Double 0.0")

    @Test
    fun negativeZeroIsOff() = assertOff(GBNumber(-0.0), "Double -0.0")

    @Test
    fun nanIsOff() = assertOff(GBNumber(Double.NaN), "Double NaN")

    // --- falsy: the rest of the JS falsy set ---------------------------------------

    @Test
    fun missingValueIsOff() = assertOff(null, "A missing value")

    @Test
    fun jsonNullIsOff() = assertOff(GBNull, "JSON null")

    @Test
    fun booleanFalseIsOff() = assertOff(GBBoolean(false), "Boolean false")

    @Test
    fun emptyStringIsOff() = assertOff(GBString(""), "The empty string")

    /**
     * [GBValue.Unknown] has no JS counterpart — it is what `GBValue.from` yields for a
     * primitive it cannot decode, and `extractFeatureValue` already maps it to `null`.
     * Reporting `on = true` for it would contradict a typed read returning `null`.
     */
    @Test
    fun unknownIsOffAndReadsBackAsNull() {
        val sdk = sdkWith(GBValue.Unknown)

        assertFalse(sdk.feature(FEATURE_KEY).on, "GBValue.Unknown should be off")
        assertTrue(sdk.feature(FEATURE_KEY).off, "GBValue.Unknown should be off")
        assertNull(
            sdk.featureValue<String>(FEATURE_KEY),
            "GBValue.Unknown reads back as null, so it must not report on = true",
        )
    }

    // --- truthy: values JS treats as truthy ----------------------------------------

    @Test
    fun nonZeroNumberIsOn() = assertOn(GBNumber(0.1), "A non-zero number")

    @Test
    fun infinityIsOn() = assertOn(GBNumber(Double.POSITIVE_INFINITY), "Infinity")

    @Test
    fun stringZeroIsOn() = assertOn(GBString("0"), "The string \"0\"")

    @Test
    fun stringFalseIsOn() = assertOn(GBString("false"), "The string \"false\"")

    @Test
    fun emptyArrayIsOn() = assertOn(GBArray(emptyList()), "An empty array")

    @Test
    fun emptyObjectIsOn() = assertOn(GBJson(emptyMap()), "An empty object")

    @Test
    fun onAndOffAreAlwaysComplementary() {
        val values = listOf(
            null, GBNull, GBValue.Unknown, GBBoolean(false), GBBoolean(true),
            GBNumber(0), GBNumber(0L), GBNumber(0.0f), GBNumber(0.0), GBNumber(-0.0),
            GBNumber(Double.NaN), GBNumber(1), GBString(""), GBString("0"),
            GBArray(emptyList()), GBJson(emptyMap()),
        )

        for (value in values) {
            val result = sdkWith(value).feature(FEATURE_KEY)
            assertEquals(!result.on, result.off, "on/off must stay complementary for $value")
        }
    }

    private companion object {
        const val FEATURE_KEY = "truthiness-flag"
    }
}
