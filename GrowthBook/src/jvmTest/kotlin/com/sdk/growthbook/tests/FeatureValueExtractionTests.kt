package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.featureValue
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
import kotlin.test.assertNull

/**
 * `featureValue<V>()` exists twice: as a member on [GrowthBookSDK] and as an extension on
 * [IGrowthBookSDK]. A member always wins over an extension, so if the two bodies ever diverge
 * the result starts depending on the *declared* type of the variable. These tests pin both
 * paths to the same answers.
 *
 * The mapping follows the reference (TypeScript) SDK's `getFeatureValue`, which returns the
 * decoded JSON value with no supported-type gate and treats arrays as ordinary values.
 */
class FeatureValueExtractionTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private fun sdkWith(value: GBValue?): GrowthBookSDK {
        val features: GBFeatures = mapOf(KEY to GBFeature(defaultValue = value))
        return GBSDKBuilder(
            apiKey = "feature-value-key",
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

    // --- member and extension must agree -------------------------------------------

    /**
     * The regression this guards: the member used to gate on a whitelist of supported types
     * while the extension did not, so `featureValue<Any>` returned null through a
     * `GrowthBookSDK`-typed variable and the value through an `IGrowthBookSDK`-typed one.
     */
    @Test
    fun supertypeRequestReturnsTheValueThroughBothPaths() {
        val sdk = sdkWith(GBString("Hi"))
        val asInterface: IGrowthBookSDK = sdk

        assertEquals("Hi", sdk.featureValue<Any>(KEY), "member path")
        assertEquals("Hi", asInterface.featureValue<Any>(KEY), "extension path")
    }

    @Test
    fun memberAndExtensionAgreeAcrossValueKinds() {
        val values = listOf(
            GBString("Hi"), GBNumber(25), GBBoolean(true), GBNull, GBValue.Unknown,
            GBJson(mapOf("a" to GBNumber(1))), GBArray(listOf(GBNumber(1), GBNumber(2))),
        )

        for (value in values) {
            val sdk = sdkWith(value)
            val asInterface: IGrowthBookSDK = sdk

            assertEquals(
                sdk.featureValue<Any>(KEY),
                asInterface.featureValue<Any>(KEY),
                "member and extension must agree for $value",
            )
        }
    }

    // --- per-kind mapping ----------------------------------------------------------

    @Test
    fun stringValueIsReadAsString() {
        assertEquals("Hi", sdkWith(GBString("Hi")).featureValue<String>(KEY))
    }

    @Test
    fun booleanValueIsReadAsBoolean() {
        assertEquals(true, sdkWith(GBBoolean(true)).featureValue<Boolean>(KEY))
    }

    @Test
    fun numberValueIsReadAsItsDecodedSubtype() {
        val sdk = sdkWith(GBNumber(25))

        assertEquals(25, sdk.featureValue<Int>(KEY))
        assertEquals(25, sdk.featureValue<Number>(KEY))
    }

    @Test
    fun jsonObjectValueIsReadAsGBJson() {
        val value = GBJson(mapOf("a" to GBNumber(1)))

        assertEquals(value, sdkWith(value).featureValue<GBJson>(KEY))
    }

    /**
     * Arrays used to fall through to null in both paths, although the reference SDK's value
     * type (`JSONValue`) includes `Array<JSONValue>` — an array-valued feature was simply
     * unreadable through `featureValue`.
     */
    @Test
    fun arrayValueIsReadAsGBArray() {
        val value = GBArray(listOf(GBNumber(1), GBNumber(2)))
        val sdk = sdkWith(value)
        val asInterface: IGrowthBookSDK = sdk

        assertEquals(value, sdk.featureValue<GBArray>(KEY))
        assertEquals(value, asInterface.featureValue<GBArray>(KEY))
    }

    /** [GBArray] implements `List<GBValue>`, so it also satisfies a list-typed read. */
    @Test
    fun arrayValueIsReadableAsList() {
        val value = GBArray(listOf(GBNumber(1), GBNumber(2)))

        assertEquals(value, sdkWith(value).featureValue<List<GBValue>>(KEY))
    }

    // --- absent and mismatched values ----------------------------------------------

    @Test
    fun typeMismatchReturnsNull() {
        val sdk = sdkWith(GBString("Hi"))

        assertNull(sdk.featureValue<Int>(KEY), "member path")
        assertNull((sdk as IGrowthBookSDK).featureValue<Int>(KEY), "extension path")
    }

    @Test
    fun jsonNullAndUnknownAndMissingValueReturnNull() {
        assertNull(sdkWith(GBNull).featureValue<String>(KEY), "JSON null")
        assertNull(sdkWith(GBValue.Unknown).featureValue<String>(KEY), "GBValue.Unknown")
        assertNull(sdkWith(null).featureValue<String>(KEY), "missing value")
        assertNull(sdkWith(GBString("Hi")).featureValue<String>("no-such-key"), "unknown key")
    }

    private companion object {
        const val KEY = "value-flag"
    }
}
