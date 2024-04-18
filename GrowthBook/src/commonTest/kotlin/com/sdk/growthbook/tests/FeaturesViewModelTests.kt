package com.sdk.growthbook.tests

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeaturesViewModelTests : FeaturesFlowDelegate {

    private var isSuccess: Boolean = false
    private var isError: Boolean = false
    private var hasFeatures: Boolean = false

    @BeforeTest
    fun setUp() {
        GrowthBookSDK.gbContext = GBContext(
            "Key", hostURL = "https://example.com",
            enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
            qaMode = false, trackingCallback = { _, _ ->

            },
            encryptionKey = null,
            remoteEval = false,
        )
    }

    @Test
    fun testSuccess() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(MockResponse.successResponse, null)), "3tfeoyW0wlo47bDnbWDkxg=="
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testSuccessForEncryptedFeatures() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.successResponseEncryptedFeatures, null
                )
            ), "3tfeoyW0wlo47bDnbWDkxg=="
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testError() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(null, Throwable("UNKNOWN", null))), null
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testInvalid() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(MockResponse.ERROR_RESPONSE, null)), ""
        )
        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        isSuccess = true
        isError = false
        hasFeatures = features.isNotEmpty()
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
        hasFeatures = false
    }

    override fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {
        isSuccess = true
        isError = false
        hasFeatures = !model.features.isNullOrEmpty()
    }
}
