package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrowthBookSDKBuilderTests {

    val testApiKey = "4r23r324f23"
    val testHostURL = "https://host.com"
    val testKeyString = "3tfeoyW0wlo47bDnbWDkxg=="
    val testAttributes: HashMap<String, Any> = HashMap()

    @BeforeTest
    fun setUp() {
    }

    @Test
    fun testSDKInitilizationDefault() {

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false
        ).initialize()

        assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
        assertTrue(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
        assertFalse(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
    }

    @Test
    fun testSDKInitilizationOverride() {

        val variations: HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false
            ).setRefreshHandler { isRefreshed, gbError ->
        }.setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
        assertTrue(sdkInstance.getGBContext().forcedVariations == variations)
    }

    @Test
    fun testSDKInitilizationData() {

        val variations: HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, gbError ->

        }
            .setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
    }

    @Test
    fun testSDKRefreshHandler() {

        var isRefreshed = false

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { _, gbError ->
            isRefreshed = true
        }.initialize()

        assertTrue(isRefreshed)

        isRefreshed = false

        sdkInstance.refreshCache()

        assertTrue(isRefreshed)
    }

    @Test
    fun testSDKFeaturesData() {

        var isRefreshed = false
        val gbError = GBError(error = null)
        val gbCacheRefreshHandler: GBCacheRefreshHandler = { _, _ -> }

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            encryptionKey = null,
            attributes = testAttributes,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { _, gbError ->
            isRefreshed = true
        }.initialize()

        assertTrue(isRefreshed)

        assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
        assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))
    }

    @Test
    fun testSDKRunMethods() {

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, gbError ->
        }.initialize()

        val featureValue = sdkInstance.feature("fwrfewrfe")
        assertEquals(featureValue.source, GBFeatureSource.unknownFeature)

        val expValue = sdkInstance.run(GBExperiment("fwewrwefw"))
        assertTrue(expValue.variationId == 0)
    }

    private fun buildSDK(
        json: String,
        attributes: Map<String, Any> = mapOf(),
        encryptionKey: String?
    ): GrowthBookSDK {
        return GBSDKBuilder(
            "some_key",
            "http://host.com",
            attributes = attributes,
            encryptionKey = encryptionKey,
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(json, null),
            remoteEval = false
        ).initialize()
    }

    @Test
    fun testSDKInitializationDataWithEncrypted() {
        // val viewModel: FeaturesViewModel()
        val variations: HashMap<String, Int> = HashMap()

        val sdkInstance = buildSDK(
            MockResponse.successResponseEncryptedFeatures,
            testAttributes,
            testKeyString
        )
        assertEquals(sdkInstance.getGBContext().attributes, testAttributes)
        assertEquals(true, sdkInstance.getFeatures() is GBFeatures)
        println("from end of decrypted test")
    }

//    @Test
//    fun testSDKInitializationJAVA() {
//
//        val sdkInstance = GBSDKBuilderJAVA(testApiKey, testHostURL, attributes = testAttributes, features = HashMap(), trackingCallback = {
//                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->
//
//
//        }).initialize()
//
//        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
//        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
//        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
//
//
//    }
//
//    @Test
//    fun testSDKFeaturesDataJAVA() {
//
//        val features : GBFeatures = HashMap()
//        features.put("onboarding", GBFeature())
//
//        val sdkInstance = GBSDKBuilderJAVA(testApiKey, testHostURL, attributes = testAttributes, features = features, trackingCallback = {
//                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->
//
//
//        }).initialize()
//
//        assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
//        assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))
//
//    }

}