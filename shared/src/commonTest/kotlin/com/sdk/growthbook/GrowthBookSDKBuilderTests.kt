package com.sdk.growthbook.Configs

import com.sdk.growthbook.Features.FeaturesDataSource
import com.sdk.growthbook.Features.FeaturesFlowDelegate
import com.sdk.growthbook.Features.FeaturesViewModel
import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.MockNetworkClient
import com.sdk.growthbook.MockResponse
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class GrowthBookSDKBuilderTests {

    val testApiKey = "4r23r324f23"
    val testHostURL = "https://host.com"
    val testAttributes : HashMap<String, Any> = HashMap()

    @BeforeTest
    fun setUp() {

    }

    @Test
    fun testSDKInitilizationDefault() {

        val sdkInstance = GBSDKBuilder(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
            gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertTrue(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().url == testHostURL)
        assertFalse(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)

    }

    @Test
    fun testSDKInitilizationOverride() {

        val sdkInstance = GBSDKBuilder(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setEnabled(false).setQAMode(true).setRefreshHandler {  }.initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().url == testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)

    }

    @Test
    fun testSDKInitilizationData() {

        val sdkInstance = GBSDKBuilder(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).setEnabled(false).setQAMode(true).setRefreshHandler {  }.initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
        assertTrue(sdkInstance.getGBContext().url == testHostURL)
        assertTrue(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)

    }

    @Test
    fun testSDKRefreshHandler() {

        var isRefreshed = false

        val sdkInstance = GBSDKBuilder(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).setRefreshHandler {
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

        val sdkInstance = GBSDKBuilder(testApiKey, testHostURL, attributes = testAttributes, trackingCallback = {
                gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->


        }).setNetworkDispatcher(MockNetworkClient(MockResponse.successResponse, null)).setRefreshHandler {
            isRefreshed = true
        }.initialize()

        assertTrue(isRefreshed)

        assertTrue(sdkInstance.getFeatures().containsKey("onboarding"))
        assertTrue(sdkInstance.getOverrides().containsKey("onboarding"))

        assertFalse(sdkInstance.getFeatures().containsKey("fwrfewrfe"))
        assertFalse(sdkInstance.getOverrides().containsKey("ferfverfe"))

    }

}