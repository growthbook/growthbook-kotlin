package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.GBSDKBuilderJAVA
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.utils.GBFeatures
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrowthBookSDKBuilderTests {

    private val testApiKey = "4r23r324f23"
    private val testHostURL = "https://host.com"
    private val testAttributes : HashMap<String, Any> = HashMap()

    @BeforeTest
    fun setUp() {

    }

    @Test
    fun testSDKInitializationDefault() {

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
            gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).initialize()

        assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
        assertTrue(sdkInstance.getGBContext().enabled)
        assertEquals(sdkInstance.getGBContext().hostURL, testHostURL)
        assertFalse(sdkInstance.getGBContext().qaMode)
        assertEquals(sdkInstance.getGBContext().attributes, testAttributes)

    }

    @Test
    fun testSDKInitializationOverride() {

        val variations : HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setRefreshHandler {  }.setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertEquals(sdkInstance.getGBContext().hostURL, testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertEquals(sdkInstance.getGBContext().attributes, testAttributes)
        assertEquals(sdkInstance.getGBContext().forcedVariations, variations)

    }

    @Test
    fun testSDKInitilizationData() {

        val variations : HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setRefreshHandler {  }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().hostURL == testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)

    }

    @Test
    fun testSDKRefreshHandler() {

        var isRefreshed = false

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setRefreshHandler {
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

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setRefreshHandler {
            isRefreshed = true
        }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).initialize()

        assertTrue(isRefreshed)

        assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
        assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))

    }

    @Test
    fun testSDKRunMethods() {

        val sdkInstance = GBSDKBuilderApp(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setRefreshHandler {

        }.setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).initialize()


        val featureValue = sdkInstance.feature("fwrfewrfe")
        assertEquals(featureValue.source, GBFeatureSource.unknownFeature)

        val expValue = sdkInstance.run(GBExperiment("fwewrwefw"))
        assertTrue(expValue.variationId == 0)
    }

   @OptIn(DelicateCoroutinesApi::class)
   @Test
   fun testSDKInitializationJAVA() {

       val sdkInstance = GBSDKBuilderJAVA(testApiKey, testHostURL, attributes = testAttributes, features = HashMap(), trackingCallback = {
               gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


       }).initialize()

       assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
       assertEquals(sdkInstance.getGBContext().hostURL, testHostURL)
       assertEquals(sdkInstance.getGBContext().attributes, testAttributes)


   }

   @OptIn(DelicateCoroutinesApi::class)
   @Test
   fun testSDKFeaturesDataJAVA() {

       val features : GBFeatures = HashMap()
       features["onboarding"] = GBFeature()

       val sdkInstance = GBSDKBuilderJAVA(testApiKey, testHostURL, attributes = testAttributes, features = features, trackingCallback = {
               gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


       }).initialize()

       assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
       assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))

   }


}