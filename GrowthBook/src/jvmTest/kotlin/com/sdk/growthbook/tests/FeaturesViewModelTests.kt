package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBOptions
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class FeaturesViewModelTests : FeaturesFlowDelegate {

    private var isSuccess: Boolean = false
    private var isError: Boolean = false
    private var hasFeatures: Boolean = false

    private val gbContext = GBContext(
        "Key",
        enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
        qaMode = false, trackingCallback = { _, _ ->

        },
        encryptionKey = null,
        remoteEval = false,
    )
    private val testGbOptions = GBOptions("https://example.com", null)

    @Test
    fun testSuccess() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(MockNetworkClient(MockResponse.successResponse, null), gbContext, testGbOptions),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
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
                ),
                gbContext, testGbOptions,
            ), "3tfeoyW0wlo47bDnbWDkxg==", false,
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
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    null, Throwable("UNKNOWN", null)
                ),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
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
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.ERROR_RESPONSE, null
                ),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )
        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testForRemoteEvalSuccess() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource =
            FeaturesDataSource(
                dispatcher = MockNetworkClient(
                    successResponse = MockResponse.successResponse,
                    error = null
                ),
                gbContext, testGbOptions
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )
        val forcedFeature = mapOf("feature" to GBNumber(123))
        val forcedVariation = mapOf("feature" to 123)
        val attributes = emptyMap<String, Any>()
        val payload = GBRemoteEvalParams(
            attributes = attributes,
            forcedFeatures = forcedFeature,
            forcedVariations = forcedVariation,
        )

        viewModel.fetchFeatures(remoteEval = true, payload = payload)
        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testForRemoteEvalFailed() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource =
            FeaturesDataSource(
                dispatcher = MockNetworkClient(
                    successResponse = null,
                    error = Error()
                ),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )
        val forcedFeature = mapOf("feature" to GBNumber(123))
        val forcedVariation = mapOf("feature" to 123)
        val attributes = emptyMap<String, Any>()
        val payload = GBRemoteEvalParams(
            attributes = attributes,
            forcedFeatures = forcedFeature,
            forcedVariations = forcedVariation
        )

        viewModel.fetchFeatures(remoteEval = true, payload = payload)

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

    override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
    }

    override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) {
        isSuccess = true
        isError = false
    }

    override fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {
        isSuccess = true
        isError = false
        hasFeatures = !model.features.isNullOrEmpty()
    }
}
