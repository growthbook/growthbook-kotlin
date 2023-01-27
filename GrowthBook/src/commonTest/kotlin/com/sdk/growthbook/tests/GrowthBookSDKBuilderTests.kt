package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.GBCacheRefreshHandler
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
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

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = "",
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).initialize()

        assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
        assertTrue(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
        assertFalse(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
    }

    @Test
    fun testSDKInitilizationOverride() {

        val variations: HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = "",
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).setRefreshHandler { isRefreshed, gbError ->
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

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = "",
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).setRefreshHandler { isRefreshed, gbError ->

        }
            .setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null))
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

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = "",
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).setRefreshHandler { _, gbError ->
            isRefreshed = true
        }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).initialize()

        assertTrue(isRefreshed)

        isRefreshed = false

        sdkInstance.refreshCache()

        assertTrue(isRefreshed)
    }

    @Test
    fun testSDKFeaturesData() {

        var isRefreshed = false
        val gbError = GBError(error = null)
        val gbCacheRefreshHandler: GBCacheRefreshHandler = { isRefreshed, gbError -> }

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            encryptionKey = testKeyString,
            attributes = testAttributes,
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).setRefreshHandler { _, gbError ->
            isRefreshed = true
        }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).initialize()

        assertTrue(isRefreshed)

        assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
        assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))
    }

    @Test
    fun testSDKRunMethods() {

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = testApiKey,
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).setRefreshHandler { isRefreshed, gbError ->
        }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).initialize()

        val featureValue = sdkInstance.feature("fwrfewrfe")
        assertEquals(featureValue.source, GBFeatureSource.unknownFeature)

        val expValue = sdkInstance.run(GBExperiment("fwewrwefw"))
        assertTrue(expValue.variationId == 0)
    }

    private fun buildSDK(
        json: String,
        attributes: Map<String, Any> = mapOf(),
        encryptionKey: String
    ): GrowthBookSDK {
        return GBSDKBuilderApp(
            "some_key",
            "http://host.com",
            attributes = attributes,
            encryptionKey = encryptionKey,
            trackingCallback = { _, _ -> }).setNetworkDispatcher(
            MockNetworkClient(
                json,
                null
            )
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