package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrowthBookSDKReactiveTests {
    private val apiKey = "key"
    private val hostURL = "https://example.com"

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun builder(client: MockNetworkClient, scheduler: TestCoroutineScheduler) =
        GBSDKBuilder(
            apiKey = apiKey,
            apiHost = hostURL,
            attributes = HashMap<String, GBValue>(),
            trackingCallback = { _, _ -> },
            networkDispatcher = client,
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(scheduler))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featuresStateFlowReflectsFetchedPayload() = runTest {
        val sdk = builder(
            MockNetworkClient(MockResponse.successResponse, null),
            testScheduler
        ).initialize()
        runCurrent()

        val features = sdk.featuresStateFlow.value
        assertTrue(features.contains("onboarding"))
        assertTrue(features.contains("qrscanpayment"))
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featuresStateFlowKeepsSeedOnNetworkFailure() = runTest {
        val seed = encryptToFeaturesDataModel("""{"seedflag":{"defaultValue":true}}""")
        val sdk = builder(
            client = MockNetworkClient(
                successResponse = null,
                error = RuntimeException("boom")
            ), scheduler = testScheduler
        )
            .setInitialFeatures(seed!!)
            .initialize()
        runCurrent()

        assertTrue(sdk.featuresStateFlow.value.contains("seedflag"))
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featuresStateFlowNetworkOverridesSeed() = runTest {
        val seed = encryptToFeaturesDataModel("""{"seedflag":{"defaultValue":true}}""")!!
        val sdk = builder(MockNetworkClient(MockResponse.successResponse, null), testScheduler)
            .setInitialFeatures(seed)
            .initialize()
        runCurrent()

        val features = sdk.featuresStateFlow.value
        assertTrue(features.containsKey("onboarding"))
        assertFalse(features.containsKey("seedflag"))
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setEncryptedFeaturesEmitsToFlow() = runTest {
        val sdk = builder(MockNetworkClient(null, null), testScheduler).initialize()
        runCurrent()
        assertFalse(sdk.featuresStateFlow.value.containsKey("testfeature1"))

        sdk.setEncryptedFeatures(
            "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx",
            "Ns04T5n9+59rl2x3SlNHtQ==",
            null,
        )
        runCurrent()

        assertTrue(sdk.featuresStateFlow.value.containsKey("testfeature1"))
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featureFlowEmitsAndDedupesIdenticalPayload() = runTest {
        val sdk = builder(MockNetworkClient(MockResponse.successResponse, null), testScheduler)
            .initialize()
        runCurrent()

        val emissions = mutableListOf<Boolean>()
        val job = backgroundScope.launch {
            sdk.featureFlow("editprofile").collect { emissions.add(it.on) }
        }
        runCurrent()
        val afterFirst = emissions.size
        assertTrue(afterFirst >= 1)
        sdk.refreshCacheSuspend()
        runCurrent()
        assertEquals(afterFirst, emissions.size, "identical payload must not re-emit")

        job.cancel()
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshCacheSuspendTrueOnSuccess() = runTest {
        val sdk = builder(MockNetworkClient(MockResponse.successResponse, null), testScheduler)
            .initialize()
        runCurrent()
        assertTrue(sdk.refreshCacheSuspend())
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshCacheSuspendTrueOnNotModifiedWithLoadedPayload() = runTest {
        // A seed makes hasFeaturesPayload true, so a subsequent 304 means the loaded payload is
        // still valid and must be reported as success.
        val seed = encryptToFeaturesDataModel("""{"seedflag":{"defaultValue":true}}""")!!
        val sdk = builder(MockNetworkClient(null, null, notModified = true), testScheduler)
            .setInitialFeatures(seed)
            .initialize()
        runCurrent()

        assertTrue(
            sdk.refreshCacheSuspend(),
            "304 with a loaded payload must count as success"
        )
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshCacheSuspendFalseOnNotModifiedWithoutPayload() = runTest {
        // No payload was ever loaded: a 304 cannot guarantee features are available, so it must be
        // reported as a failure (mirrors featuresNotModified()) rather than a false success.
        val sdk = builder(MockNetworkClient(null, null, notModified = true), testScheduler)
            .initialize()
        runCurrent()

        assertFalse(
            sdk.refreshCacheSuspend(),
            "304 before any payload has loaded must not report success"
        )
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshCacheSuspendFalseOnFailure() = runTest {
        val sdk = builder(MockNetworkClient(null, RuntimeException("boom")), testScheduler)
            .initialize()
        runCurrent()
        assertFalse(sdk.refreshCacheSuspend())
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featureFlowReEmitsWhenAttributesChange() = runTest {
        // "targeted" gates on attribute `country == "US"`. featureFlow must re-emit when attributes
        // change even though the feature map itself is unchanged (regression guard for #4).
        val features = encryptToFeaturesDataModel(
            """{"targeted":{"defaultValue":false,"rules":[{"condition":{"country":"US"},"force":true}]}}"""
        )!!
        val sdk = builder(MockNetworkClient(null, null), testScheduler)
            .setInitialFeatures(features)
            .initialize()
        runCurrent()

        val emissions = mutableListOf<Boolean>()
        val job = backgroundScope.launch {
            sdk.featureFlow("targeted").collect { emissions.add(it.on) }
        }
        runCurrent()
        assertEquals(listOf(false), emissions, "initial emission evaluates with no matching attribute")

        sdk.setAttributes(mapOf("country" to GBString("US")))
        runCurrent()

        assertEquals(
            listOf(false, true),
            emissions,
            "featureFlow must re-evaluate and emit when attributes change the result"
        )
        job.cancel()
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featureFlowObservationDoesNotFireTrackingButFeatureDoes() = runTest {
        // "expfeature" is backed by an experiment rule, so evaluating it buckets the user and would
        // fire the experiment-exposure trackingCallback. featureFlow is a passive observer: its
        // (silent) re-evaluations must NOT fire tracking, and must NOT poison the shared tracking
        // dedup (otherwise the real exposure on feature() would be suppressed). An explicit feature()
        // access must fire the exposure exactly once.
        val features = encryptToFeaturesDataModel(
            """{"expfeature":{"defaultValue":0,"rules":[{"key":"exp1","coverage":1,""" +
                """"hashAttribute":"id","variations":[0,1],"weights":[0.5,0.5]}]}}"""
        )!!
        var trackCount = 0
        val sdk = GBSDKBuilder(
            apiKey = apiKey,
            apiHost = hostURL,
            attributes = hashMapOf<String, GBValue>("id" to GBString("user-1")),
            trackingCallback = { _, _ -> trackCount++ },
            networkDispatcher = MockNetworkClient(null, null),
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
            .setInitialFeatures(features)
            .initialize()
        runCurrent()

        val emissions = mutableListOf<Boolean>()
        val job = backgroundScope.launch {
            sdk.featureFlow("expfeature").collect { emissions.add(it.on) }
        }
        runCurrent()

        // Drive several reactive re-evaluations via unrelated state changes (id stays "user-1", so the
        // assigned variation is unchanged) — none of these must fire an exposure.
        sdk.setAttributes(mapOf("id" to GBString("user-1"), "extra" to GBString("a")))
        runCurrent()
        sdk.setForcedFeatures(emptyMap())
        runCurrent()

        assertTrue(emissions.isNotEmpty(), "featureFlow should have emitted at least once")
        assertEquals(0, trackCount, "featureFlow observation must not fire experiment tracking")

        // Explicit access fires the exposure once — proving the silent path did not poison the dedup.
        sdk.feature("expfeature")
        runCurrent()
        assertEquals(1, trackCount, "explicit feature() access must fire the exposure exactly once")

        job.cancel()
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featureFlowObservationDoesNotFirePluginsButFeatureDoes() = runTest {
        // Same contract as the trackingCallback test above, for the plugin path: the evaluators call
        // pluginRegistry.fireFeatureEvaluated/fireExperimentViewed right next to onFeatureUsage and
        // trackingCallback, so a silent (reactive) evaluation must mute the registry too. Otherwise
        // the built-in tracking plugin POSTs an exposure for every featureFlow emission — and since
        // the exposure fire is gated by GBExperimentHelper.isTracked() while a silent eval gets a
        // fresh, empty helper, nothing would ever dedup it.
        val features = encryptToFeaturesDataModel(
            """{"expfeature":{"defaultValue":0,"rules":[{"key":"exp1","coverage":1,""" +
                """"hashAttribute":"id","variations":[0,1],"weights":[0.5,0.5]}]}}"""
        )!!
        var evaluatedCount = 0
        var viewedCount = 0
        val plugin = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(
                featureKey: String,
                result: GBFeatureResult,
                attributes: Map<String, GBValue>?
            ) {
                evaluatedCount++
            }

            override fun onExperimentViewed(
                experiment: GBExperiment,
                result: GBExperimentResult,
                attributes: Map<String, GBValue>?
            ) {
                viewedCount++
            }
        }
        val sdk = GBSDKBuilder(
            apiKey = apiKey,
            apiHost = hostURL,
            attributes = hashMapOf<String, GBValue>("id" to GBString("user-1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
            .setInitialFeatures(features)
            .setPlugins(listOf(plugin))
            .initialize()
        runCurrent()

        val emissions = mutableListOf<Boolean>()
        val job = backgroundScope.launch {
            sdk.featureFlow("expfeature").collect { emissions.add(it.on) }
        }
        runCurrent()

        sdk.setAttributes(mapOf("id" to GBString("user-1"), "extra" to GBString("a")))
        runCurrent()
        sdk.setForcedFeatures(emptyMap())
        runCurrent()

        assertTrue(emissions.isNotEmpty(), "featureFlow should have emitted at least once")
        assertEquals(0, evaluatedCount, "featureFlow observation must not fire onFeatureEvaluated")
        assertEquals(0, viewedCount, "featureFlow observation must not fire onExperimentViewed")

        // Explicit access still reaches the plugins, exactly once for the exposure.
        sdk.feature("expfeature")
        runCurrent()
        assertEquals(1, evaluatedCount, "explicit feature() access must reach onFeatureEvaluated")
        assertEquals(1, viewedCount, "explicit feature() access must fire the exposure exactly once")

        job.cancel()
        sdk.close()
    }
}
