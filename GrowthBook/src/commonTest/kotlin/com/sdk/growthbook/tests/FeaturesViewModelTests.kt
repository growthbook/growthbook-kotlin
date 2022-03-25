package com.sdk.growthbook.tests

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeaturesViewModelTests : FeaturesFlowDelegate {

    var isSuccess: Boolean = false
    var isError: Boolean = false

    @BeforeTest
    fun setUp() {
        GrowthBookSDK.gbContext = GBContext("Key", hostURL = "https://example.com",
            enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
            qaMode = false, trackingCallback = { _, _ ->

            })
    }

    @Test
    fun testSuccess() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(MockResponse.successResponse, null))
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testError() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(null, Throwable("UNKNOWN", null)))
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    @Test
    fun testInvalid() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(MockResponse.errorResponse, null))
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        isSuccess = true
        isError = false
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
    }
}