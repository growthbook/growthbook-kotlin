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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeaturesViewModelTests : FeaturesFlowDelegate {

    private var isSuccess: Boolean = false
    private var isError: Boolean = false
    private var hasFeatures: Boolean = false
    private var featuresAPIModelCalled: Boolean = false
    private var receivedFromCache: Boolean = false
    private var receivedCacheError: Boolean = false

    private val gbContext = GBContext(
        "Key",
        enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
        qaMode = false,
        trackingCallback = { _, _ ->

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
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
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
            ),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
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

    @Test
    fun testSuccessWithCachingEnabled() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = true,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testFeaturesAPIModelSuccessfullyCalled() {
        featuresAPIModelCalled = false
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(featuresAPIModelCalled)
    }

    @Test
    fun testSavedGroupsFetchedSuccessfully() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithSavedGroups, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testSavedGroupsFetchFailed() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithEncryptedFeaturesOnly, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalPlainFeatures() {
        receivedFromCache = false
        val cacheLayer = MockCachingLayer.fromApiResponse(MockResponse.successResponse)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalEncryptedFeatures() {
        receivedFromCache = false
        val cacheLayer =
            MockCachingLayer.fromApiResponse(MockResponse.successResponseEncryptedFeatures)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseEncryptedFeatures, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from encrypted cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testFetchFeaturesWithCacheException() {
        receivedCacheError = false
        val cacheLayer = MockCachingLayer(throwOnGet = true)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(null, Throwable("Network error")),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedCacheError,
            "Expected featuresFetchFailed(isRemote=false) from cache exception"
        )
        assertTrue(isError)
        assertTrue(!isSuccess)
    }

    @Test
    fun testAutoRefreshFeaturesReturnsFlow() {
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )

        val flow = viewModel.autoRefreshFeatures()

        assertNotNull(flow)
    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        isSuccess = true
        isError = false
        hasFeatures = features.isNotEmpty()
        if (!isRemote) receivedFromCache = true
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
        hasFeatures = false
        if (!isRemote) receivedCacheError = true
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
        featuresAPIModelCalled = true
    }
}
