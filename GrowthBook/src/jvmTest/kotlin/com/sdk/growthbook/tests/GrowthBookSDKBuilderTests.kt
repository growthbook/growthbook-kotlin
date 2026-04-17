package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.toGbBoolean
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrowthBookSDKBuilderTests {

    val testApiKey = "4r23r324f23"
    val testHostURL = "https://host.com"
    val testKeyString = "3tfeoyW0wlo47bDnbWDkxg=="
    val testAttributes: HashMap<String, GBValue> = HashMap()

    @BeforeTest
    fun setUp() {
    }

    @Test
    fun testSDKInitilizationDefault() {

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false
        ).initialize()

        assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
        assertTrue(sdkInstance.getGBContext().enabled)
        assertFalse(sdkInstance.getGBContext().qaMode)
        assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
    }

    @Test
    fun testSDKInitilizationWithCallback() {

        GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false
        ).initialize { sdkInstance ->
            assertEquals(sdkInstance.getGBContext().apiKey, testApiKey)
            assertTrue(sdkInstance.getGBContext().enabled)
            assertFalse(sdkInstance.getGBContext().qaMode)
            assertTrue(sdkInstance.getGBContext().attributes == testAttributes)
        }
    }

    @Test
    fun testSDKInitilizationOverride() {

        val variations: HashMap<String, Int> = HashMap()

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, gbError ->
        }.setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
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
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, gbError ->

        }
            .setEnabled(false).setForcedVariations(variations).setQAMode(true).initialize()

        assertTrue(sdkInstance.getGBContext().apiKey == testApiKey)
        assertFalse(sdkInstance.getGBContext().enabled)
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
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
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
    fun testSDKRefreshHandlerCalledOn304NotModified() {
        var refreshCalled = false
        var refreshSuccess: Boolean? = null

        GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(successResponse = null, error = null, notModified = true),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, _ ->
            refreshCalled = true
            refreshSuccess = isRefreshed
        }.initialize()

        assertTrue(refreshCalled)
        assertEquals(true, refreshSuccess)
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
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
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
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setRefreshHandler { isRefreshed, gbError ->
        }.initialize()

        val featureValue = sdkInstance.feature("fwrfewrfe")
        assertEquals(featureValue.source, GBFeatureSource.unknownFeature)

        val expValue = sdkInstance.run(GBExperiment("fwewrwefw"))
        assertTrue(expValue.variationId == 0)
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

    @Test
    fun test_setPrefixForStickyBucketCachedDirectory_Ok() {
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setPrefixForStickyBucketCachedDirectory(
            coroutineScope = TestScope(), prefix = "test_prefix",
        ).initialize()

        assertTrue { sdkInstance.getGBContext().stickyBucketService != null }
        assertTrue { sdkInstance.getGBContext().stickyBucketService is GBStickyBucketServiceImp }
    }

    @Test
    fun test_setStickyBucketService_Ok() {
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setStickyBucketService(TestScope())
            .initialize()

        assertTrue { sdkInstance.getGBContext().stickyBucketService != null }
        assertTrue { sdkInstance.getGBContext().stickyBucketService is GBStickyBucketServiceImp }
    }

    @Test
    fun test_setForcedVariations_Ok() {
        val expectedForcedVariation = mapOf("user" to 1234)
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        ).setForcedVariations(expectedForcedVariation)
            .initialize()

        val actualForcedVariation = sdkInstance.getGBContext().forcedVariations

        assertTrue { actualForcedVariation.isNotEmpty() }
        assertEquals(actualForcedVariation, expectedForcedVariation)
    }

    @Test
    fun test_setForcedVariationsWithRemoteEval_Ok() {
        val expectedForcedVariation = mapOf("user" to 1234)
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = true
        ).setForcedVariations(expectedForcedVariation)
            .initialize()
        sdkInstance.setForcedFeatures(
            mapOf("featureForce" to GBNumber(112))
        )

        val actualForcedVariation = sdkInstance.getGBContext().forcedVariations

        assertTrue { actualForcedVariation.isNotEmpty() }
        assertEquals(actualForcedVariation, expectedForcedVariation)
        assertTrue { sdkInstance.getForcedFeatures().isNotEmpty() }
    }

    @Test
    fun test_setAttributesOverrides_Ok() {
        val expectedAttributes = mapOf("user" to false.toGbBoolean())
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = expectedAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = false
        )
            .initialize()

        sdkInstance.setAttributeOverrides(expectedAttributes)

        val actualAttributesOverrides = sdkInstance.getAttributeOverrides()

        assertTrue { actualAttributesOverrides.isNotEmpty() }
        assertEquals(actualAttributesOverrides, expectedAttributes)
    }

    @Test
    fun test_setAttributesOverridesWithStickyBucketing_Ok() {
        val expectedAttributes = mapOf("user" to GBBoolean(false))
        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = expectedAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(MockResponse.successResponse, null),
            remoteEval = true
        ).setStickyBucketService(GBStickyBucketServiceImp(TestScope()))
            .initialize()

        sdkInstance.setAttributeOverrides(expectedAttributes)

        val actualAttributesOverrides = sdkInstance.getAttributeOverrides()

        assertTrue { actualAttributesOverrides.isNotEmpty() }
        assertTrue { sdkInstance.getGBContext().stickyBucketService != null }
        assertEquals(actualAttributesOverrides, expectedAttributes)
    }

    @Test
    fun test_setAttributesSync_updatesContextAttributes() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        val newAttributes = mapOf("userId" to GBString("user-123"))

        sdk.setAttributesSync(newAttributes)

        assertEquals(newAttributes, sdk.getGBContext().attributes)
    }

    @Test
    fun test_setAttributesSync_replacesOldAttributes() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        sdk.setAttributesSync(mapOf("old" to GBString("v")))

        sdk.setAttributesSync(mapOf("new" to GBString("y")))

        assertNull(sdk.getGBContext().attributes["old"])
        assertEquals("y", (sdk.getGBContext().attributes["new"] as? GBString)?.value)
    }

    @Test
    fun test_setAttributesSync_withEmptyMap_clearsAttributes() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        sdk.setAttributesSync(mapOf("key" to GBString("val")))

        sdk.setAttributesSync(emptyMap())

        assertTrue(sdk.getGBContext().attributes.isEmpty())
    }

    @Test
    fun test_setAttributesSync_withoutStickyBucket_doesNotThrow() = TestScope().runTest {
        val sdk = buildSdkWithHandler(withStickyBucket = false)

        sdk.setAttributesSync(mapOf("plan" to GBString("premium")))

        assertEquals("premium", (sdk.getGBContext().attributes["plan"] as? GBString)?.value)
    }

    @Test
    fun test_setAttributesSync_withStickyBucket_initialisesStickyBucketAssignmentDocs() =
        TestScope().runTest {
            val sdk = buildSdkWithHandler(withStickyBucket = true)

            sdk.setAttributesSync(mapOf("id" to GBString("user-abc")))

            assertNotNull(sdk.getGBContext().stickyBucketAssignmentDocs)
        }

    @Test
    fun test_savedGroupsFetchFailed_isRemoteTrue_callsRefreshHandlerWithFalse() {
        var handlerSuccess: Boolean? = null
        val sdk = buildSdkWithHandler(refreshHandler = { success, _ -> handlerSuccess = success })

        sdk.savedGroupsFetchFailed(GBError(error = null), isRemote = true)

        assertEquals(false, handlerSuccess)
    }

    @Test
    fun test_savedGroupsFetchFailed_isRemoteFalse_doesNotCallRefreshHandler() {
        var handlerCallCount = 0
        val sdk = buildSdkWithHandler(refreshHandler = { _, _ -> handlerCallCount++ })
        val countAfterInit = handlerCallCount

        sdk.savedGroupsFetchFailed(GBError(error = null), isRemote = false)

        assertEquals(countAfterInit, handlerCallCount)
    }

    @Test
    fun test_savedGroupsFetchFailed_passesErrorToHandler() {
        val expectedError = GBError(error = RuntimeException("network failure"))
        var receivedError: GBError? = null
        val sdk = buildSdkWithHandler(refreshHandler = { _, error -> receivedError = error })

        sdk.savedGroupsFetchFailed(expectedError, isRemote = true)

        assertEquals(expectedError, receivedError)
    }

    @Test
    fun test_savedGroupsFetchedSuccessfully_updatesSavedGroups() {
        val sdk = buildSdkWithHandler()
        val jsonGroups = buildJsonObject {
            put("premium", JsonPrimitive(true))
            put("beta", JsonPrimitive("yes"))
        }

        sdk.savedGroupsFetchedSuccessfully(jsonGroups, isRemote = false)

        val savedGroups = sdk.getGBContext().savedGroups
        assertNotNull(savedGroups)
        assertTrue(savedGroups.containsKey("premium"))
        assertTrue(savedGroups.containsKey("beta"))
    }

    @Test
    fun test_savedGroupsFetchedSuccessfully_isRemoteTrue_callsRefreshHandlerWithTrue() {
        var handlerSuccess: Boolean? = null
        var handlerError: GBError? = GBError(null)
        val sdk = buildSdkWithHandler(refreshHandler = { success, error ->
            handlerSuccess = success
            handlerError = error
        })

        sdk.savedGroupsFetchedSuccessfully(
            buildJsonObject { put("g1", JsonPrimitive(1)) },
            isRemote = true
        )

        assertEquals(true, handlerSuccess)
        assertNull(handlerError)
    }

    @Test
    fun test_savedGroupsFetchedSuccessfully_isRemoteFalse_doesNotCallRefreshHandler() {
        var handlerCallCount = 0
        val sdk = buildSdkWithHandler(refreshHandler = { _, _ -> handlerCallCount++ })
        val countAfterInit = handlerCallCount

        sdk.savedGroupsFetchedSuccessfully(
            buildJsonObject { put("g1", JsonPrimitive(1)) },
            isRemote = false
        )

        assertEquals(countAfterInit, handlerCallCount)
    }

    @Test
    fun test_savedGroupsFetchedSuccessfully_withEmptyJson_setEmptySavedGroups() {
        val sdk = buildSdkWithHandler()

        sdk.savedGroupsFetchedSuccessfully(buildJsonObject { }, isRemote = false)

        val savedGroups = sdk.getGBContext().savedGroups
        assertNotNull(savedGroups)
        assertTrue(savedGroups.isEmpty())
    }

    @Test
    fun test_setAttributeOverridesSync_updatesOverrides() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        val overrides = mapOf("country" to GBString("UA"))

        sdk.setAttributeOverridesSync(overrides)

        assertEquals(overrides, sdk.getAttributeOverrides())
    }

    @Test
    fun test_setAttributeOverridesSync_replacesOldOverrides() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        sdk.setAttributeOverridesSync(mapOf("old" to GBString("x")))

        sdk.setAttributeOverridesSync(mapOf("new" to GBString("z")))

        assertNull(sdk.getAttributeOverrides()["old"])
        assertEquals("z", (sdk.getAttributeOverrides()["new"] as? GBString)?.value)
    }

    @Test
    fun test_setAttributeOverridesSync_withEmptyMap_clearsOverrides() = TestScope().runTest {
        val sdk = buildSdkWithHandler()
        sdk.setAttributeOverridesSync(mapOf("k" to GBString("v")))

        sdk.setAttributeOverridesSync(emptyMap())

        assertTrue(sdk.getAttributeOverrides().isEmpty())
    }

    @Test
    fun test_setAttributeOverridesSync_withoutStickyBucket_doesNotThrow() = TestScope().runTest {
        val sdk = buildSdkWithHandler(withStickyBucket = false)

        sdk.setAttributeOverridesSync(mapOf("env" to GBString("prod")))

        assertEquals("prod", (sdk.getAttributeOverrides()["env"] as? GBString)?.value)
    }

    @Test
    fun test_setAttributeOverridesSync_withStickyBucket_initialisesStickyBucketAssignmentDocs() =
        TestScope().runTest {
            val sdk = buildSdkWithHandler(withStickyBucket = true)

            sdk.setAttributeOverridesSync(mapOf("id" to GBString("override-user")))

            assertNotNull(sdk.getGBContext().stickyBucketAssignmentDocs)
        }

    @Test
    fun test_setAttributeOverridesSync_withRemoteEval_triggersNetworkFetch() =
        TestScope().runTest {
            var postCount = 0
            val networkClient = object : MockNetworkClient(MockResponse.successResponse, null) {
                override fun consumePOSTRequest(
                    url: String,
                    bodyParams: Map<String, Any>,
                    onSuccess: (String) -> Unit,
                    onError: (Throwable) -> Unit
                ) {
                    postCount++
                    super.consumePOSTRequest(url, bodyParams, onSuccess, onError)
                }
            }
            val sdk = GBSDKBuilder(
                apiKey = testApiKey,
                apiHost = testHostURL,
                attributes = testAttributes,
                encryptionKey = null,
                trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
                networkDispatcher = networkClient,
                remoteEval = true,
            ).initialize()
            val countBeforeCall = postCount

            sdk.setAttributeOverridesSync(mapOf("plan" to GBString("pro")))

            assertTrue(postCount > countBeforeCall)
        }

    @Test
    fun test_refreshStickyBucketService_withoutService_contextHasNoStickyBucketService() {
        val sdk = buildSdkWithHandler(withStickyBucket = false)

        sdk.setAttributes(mapOf("id" to GBString("user-1")))

        assertNull(sdk.getGBContext().stickyBucketService)
    }

    @Test
    fun test_refreshStickyBucketService_withService_stickyBucketServicePresentInContext() {
        val sdk = buildSdkWithHandler(withStickyBucket = true)

        sdk.setAttributes(mapOf("id" to GBString("user-1")))

        assertNotNull(sdk.getGBContext().stickyBucketService)
        assertTrue(sdk.getGBContext().stickyBucketService is GBStickyBucketServiceImp)
    }

    private fun buildSDK(
        json: String,
        attributes: Map<String, GBValue> = mapOf(),
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

    private fun buildSdkWithHandler(
        remoteEval: Boolean = false,
        withStickyBucket: Boolean = false,
        networkResponse: String? = MockResponse.successResponse,
        refreshHandler: ((Boolean, GBError?) -> Unit)? = null,
    ): GrowthBookSDK {
        val testScope = TestScope()
        val builder = GBSDKBuilder(
            apiKey = testApiKey,
            apiHost = testHostURL,
            attributes = testAttributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(networkResponse, null),
            remoteEval = remoteEval,
        )
        if (refreshHandler != null) builder.setRefreshHandler(refreshHandler)
        if (withStickyBucket) builder.setStickyBucketService(testScope)
        return builder.initialize()
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
