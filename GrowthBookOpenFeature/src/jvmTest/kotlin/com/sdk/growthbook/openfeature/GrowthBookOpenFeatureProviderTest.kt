@file:OptIn(ExperimentalIsolatedApi::class, ExperimentalTime::class)

package com.sdk.growthbook.openfeature

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import dev.openfeature.kotlin.sdk.Client
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPIInstance
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import dev.openfeature.kotlin.sdk.isolated.ExperimentalIsolatedApi
import dev.openfeature.kotlin.sdk.isolated.createOpenFeatureAPIInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GrowthBookOpenFeatureProviderTest {
    private val providers = mutableListOf<GrowthBookOpenFeatureProvider>()

    @AfterTest
    fun tearDown() {
        providers.forEach { it.shutdown() }
    }

    // Typed evaluation

    @Test
    fun typedEvaluations() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        assertEquals(true, client.getBooleanDetails("bool-flag", false).value)
        assertEquals("hello", client.getStringDetails("string-flag", "").value)
        assertEquals(42, client.getIntegerDetails("int-flag", 0).value)
        assertEquals(42L, client.getLongDetails("int-flag", 0L).value)
        assertEquals(42.0, client.getDoubleDetails("int-flag", 0.0).value)
        assertEquals(0.1, client.getDoubleDetails("decimal-flag", 0.0).value)

        val details = client.getStringDetails("string-flag", "")
        assertNull(details.errorCode)
        assertEquals(Reason.DEFAULT.name, details.reason)
    }

    @Test
    fun objectEvaluationPreservesNestedValues() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        assertEquals(
            Value.Structure(
                mapOf(
                    "list" to Value.List(
                        listOf(Value.Integer(1), Value.Null, Value.Structure(mapOf("nested" to Value.String("yes"))))
                    ),
                    "ratio" to Value.Double(0.5),
                )
            ),
            client.getObjectDetails("json-flag", Value.Null).value,
        )
    }

    @Test
    fun integersRejectFractionsAndOutOfRangeValues() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        val fraction = client.getIntegerDetails("decimal-flag", 7)
        assertEquals(7, fraction.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, fraction.errorCode)

        assertEquals(ErrorCode.TYPE_MISMATCH, client.getIntegerDetails("long-flag", 7).errorCode)
        assertEquals(5000000000L, client.getLongDetails("long-flag", 7L).value)
        assertEquals(ErrorCode.TYPE_MISMATCH, client.getLongDetails("big-flag", 7L).errorCode)
    }

    @Test
    fun typeMismatchReturnsDefault() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        val details = client.getBooleanDetails("string-flag", true)
        assertEquals(true, details.value)
        assertEquals(ErrorCode.TYPE_MISMATCH, details.errorCode)

        // No truthiness: a number is not a boolean.
        assertEquals(ErrorCode.TYPE_MISMATCH, client.getBooleanDetails("int-flag", false).errorCode)
    }

    @Test
    fun topLevelNullReturnsDefault() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        val details = client.getStringDetails("null-flag", "fallback")
        assertEquals("fallback", details.value)
        assertNull(details.errorCode)
        assertEquals(Reason.DEFAULT.name, details.reason)
    }

    @Test
    fun missingFlagReturnsFlagNotFound() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        val details = client.getStringDetails("missing", "fallback")
        assertEquals("fallback", details.value)
        assertEquals(ErrorCode.FLAG_NOT_FOUND, details.errorCode)
    }

    @Test
    fun cyclicPrerequisiteReturnsParseError() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features))

        assertEquals(ErrorCode.PARSE_ERROR, client.getBooleanDetails("cyclic-flag", false).errorCode)
    }

    // Targeting and identity

    @Test
    fun targetingKeyBecomesId() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.features), context = ImmutableContext("user-1"))

        val details = client.getStringDetails("id-flag", "")
        assertEquals("matched", details.value)
        assertEquals(Reason.TARGETING_MATCH.name, details.reason)
    }

    @Test
    fun targetingKeyWinsByDefault() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            context = ImmutableContext("user-1", mapOf("id" to Value.String("other"))),
        )

        assertEquals("matched", client.getStringDetails("id-flag", "").value)
    }

    @Test
    fun idAttributeWinsWhenConfigured() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            identityPrecedence = IdentityPrecedence.ID_ATTRIBUTE,
            context = ImmutableContext("other", mapOf("id" to Value.String("user-1"))),
        )

        assertEquals("matched", client.getStringDetails("id-flag", "").value)
    }

    @Test
    fun targetingKeyFillsNullIdWhenIdAttributeWins() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            identityPrecedence = IdentityPrecedence.ID_ATTRIBUTE,
            context = ImmutableContext("user-1", mapOf("id" to Value.Null)),
        )

        assertEquals("matched", client.getStringDetails("id-flag", "").value)
    }

    @Test
    fun emptyContextKeepsConfiguredAttributes() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            attributes = mapOf("country" to GBString("CA")),
        )

        assertEquals("ca", client.getStringDetails("country-flag", "").value)
    }

    @Test
    fun initialContextReplacesConfiguredAttributes() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            attributes = mapOf("country" to GBString("CA")),
            context = ImmutableContext("user-1"),
        )

        assertEquals("default", client.getStringDetails("country-flag", "").value)
    }

    @Test
    fun instantContextValuesBecomeIsoStrings() {
        assertEquals(
            GBString("1970-01-01T00:00:00Z"),
            ValueConversion.attribute(Value.Instant(Instant.fromEpochSeconds(0))),
        )
    }

    // Context changes

    @Test
    fun contextChangesReplaceAttributes() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        val client = makeClient(api, FakeNetwork(Payloads.features), context = ImmutableContext("user-1"))
        assertEquals("matched", client.getStringDetails("id-flag", "").value)

        api.setEvaluationContextAndWait(ImmutableContext("user-2"))
        assertEquals("default", client.getStringDetails("id-flag", "").value)

        api.setEvaluationContextAndWait(ImmutableContext(attributes = mapOf("country" to Value.String("CA"))))
        assertEquals("default", client.getStringDetails("id-flag", "").value)
        assertEquals("ca", client.getStringDetails("country-flag", "").value)

        // Logout drops the previous user's attributes.
        api.setEvaluationContextAndWait(ImmutableContext())
        assertEquals("default", client.getStringDetails("country-flag", "").value)
    }

    @Test
    fun evaluationAppliesContextBeforeProviderIsNotified() = runBlocking<Unit> {
        val provider = makeProvider(FakeNetwork(Payloads.features))
        provider.initialize(ImmutableContext("user-1"))

        val result = provider.getStringEvaluation("id-flag", "", ImmutableContext("user-2"))
        assertEquals("default", result.value)
    }

    // Experiments

    @Test
    fun experimentReportsVariantAndTracksExposureOnce() = runBlocking<Unit> {
        val exposures = mutableListOf<String>()
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            context = ImmutableContext("user-1"),
            trackingCallback = { experiment, _ -> exposures += experiment.key },
        )

        val details = client.getStringDetails("experiment-flag", "")
        assertEquals("treatment", details.value)
        assertEquals("1", details.variant)
        assertEquals(Reason.SPLIT.name, details.reason)
        assertEquals(listOf("experiment-flag"), exposures)
    }

    @Test
    fun trackingCallbackRunsAfterTheProviderLockIsReleased() = runBlocking<Unit> {
        var otherThreadFinished = false
        var client: Client? = null
        client = makeClient(
            network = FakeNetwork(Payloads.features),
            // The SDK reports each user's exposure once, so use a user no other test tracks.
            context = ImmutableContext("lock-test-user"),
            trackingCallback = { _, _ ->
                // Waiting on another thread that evaluates a flag deadlocks if the callback runs under the lock.
                val done = java.util.concurrent.CountDownLatch(1)
                Thread {
                    client?.getStringValue("string-flag", "")
                    done.countDown()
                }.start()
                otherThreadFinished = done.await(2, java.util.concurrent.TimeUnit.SECONDS)
            },
        )

        assertEquals("treatment", client.getStringValue("experiment-flag", ""))
        assertTrue(otherThreadFinished)
    }

    @Test
    fun failingTrackingCallbackDoesNotFailEvaluation() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(Payloads.features),
            context = ImmutableContext("throwing-callback-user"),
            trackingCallback = { _, _ -> throw IllegalStateException("analytics down") },
        )

        val details = client.getStringDetails("experiment-flag", "")
        assertEquals("treatment", details.value)
        assertNull(details.errorCode)
    }

    // Initialization

    @Test
    fun fetchedEmptyPayloadMakesProviderReady() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        makeClient(api, FakeNetwork("""{"features": {}}"""))

        assertEquals(OpenFeatureStatus.Ready, api.getStatus())
    }

    @Test
    fun suppliedPayloadMakesProviderReadyWithoutNetwork() = runBlocking<Unit> {
        val client = makeClient(
            network = FakeNetwork(null, respond = false),
            configureSdk = { setInitialPayload(Payloads.features) },
        )

        assertEquals("hello", client.getStringDetails("string-flag", "").value)
    }

    @Test
    fun cachedFeaturesMakeProviderReadyWithoutNetwork() = runBlocking<Unit> {
        val cache = MemoryCache()
        val warm = makeProvider(FakeNetwork(Payloads.features), cache = cache)
        warm.initialize(null)
        warm.shutdown()

        val client = makeClient(network = FakeNetwork(null, respond = false), cache = cache)

        assertEquals("hello", client.getStringDetails("string-flag", "").value)
    }

    @Test
    fun failedFetchWithoutDataReportsError() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        val client = makeClient(api, FakeNetwork(null))

        assertIs<OpenFeatureStatus.Error>(api.getStatus())
        val details = client.getStringDetails("string-flag", "fallback")
        assertEquals("fallback", details.value)
        assertEquals(ErrorCode.PROVIDER_NOT_READY, details.errorCode)
    }

    @Test
    fun initializationTimesOut() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        makeClient(api, FakeNetwork(null, respond = false), initializationTimeout = 100.milliseconds)

        assertIs<OpenFeatureStatus.Error>(api.getStatus())
    }

    @Test
    fun encryptedPayload() = runBlocking<Unit> {
        val client = makeClient(network = FakeNetwork(Payloads.encryptedFeatures), encryptionKey = Payloads.ENCRYPTION_KEY)

        assertEquals("hello", client.getStringDetails("greeting", "").value)
    }

    @Test
    fun rejectsStickyBucketing() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        val client = makeClient(
            api,
            FakeNetwork(Payloads.features),
            configureSdk = { setStickyBucketService(CoroutineScope(Dispatchers.Default)) },
        )

        // OpenFeature Kotlin 0.9.0 reports every initialization failure as Error, never Fatal.
        assertIs<OpenFeatureStatus.Error>(api.getStatus())
        assertEquals(ErrorCode.PROVIDER_FATAL, client.getStringDetails("string-flag", "").errorCode)
    }

    // Updates

    @Test
    fun refreshEmitsConfigurationChangedOnlyForRealChanges() = runBlocking<Unit> {
        val network = FakeNetwork(Payloads.features)
        val provider = makeProvider(network)
        provider.initialize(null)

        val changed = async(start = CoroutineStart.UNDISPATCHED) {
            provider.observe().filterIsInstance<OpenFeatureProviderEvents.ProviderConfigurationChanged>().first()
        }

        // Same payload: no event.
        provider.sdk!!.refreshCache()
        network.response = """{"features": {"string-flag": {"defaultValue": "updated"}}}"""
        provider.sdk!!.refreshCache()

        val event = withTimeout(2.seconds) { changed.await() }
        assertTrue("string-flag" in event.eventDetails!!.flagsChanged)
        assertEquals("updated", provider.getStringEvaluation("string-flag", "", null).value)
    }

    @Test
    fun refreshFailureKeepsServingFlags() = runBlocking<Unit> {
        val network = FakeNetwork(Payloads.features)
        val provider = makeProvider(network)
        provider.initialize(null)

        val stale = async(start = CoroutineStart.UNDISPATCHED) {
            provider.observe().filterIsInstance<OpenFeatureProviderEvents.ProviderStale>().first()
        }
        network.response = null
        provider.sdk!!.refreshCache()

        withTimeout(2.seconds) { stale.await() }
        assertEquals("hello", provider.getStringEvaluation("string-flag", "", null).value)
    }

    // Shutdown

    @Test
    fun shutdownStopsEvaluation() = runBlocking<Unit> {
        val api = createOpenFeatureAPIInstance()
        val provider = makeProvider(FakeNetwork(Payloads.features))
        api.setProviderAndWait(provider)

        provider.shutdown()
        provider.shutdown()

        val details = api.getClient().getStringDetails("string-flag", "fallback")
        assertEquals("fallback", details.value)
        assertEquals(ErrorCode.PROVIDER_FATAL, details.errorCode)
    }

    @Test
    fun metadataName() {
        assertEquals("GrowthBook", GrowthBookOpenFeatureProvider("key", "https://example.test", FakeNetwork(null)).metadata.name)
    }

    // Helpers

    private fun makeProvider(
        network: FakeNetwork,
        attributes: Map<String, GBValue> = emptyMap(),
        encryptionKey: String? = null,
        identityPrecedence: IdentityPrecedence = IdentityPrecedence.TARGETING_KEY,
        initializationTimeout: Duration = 2.seconds,
        cache: MemoryCache = MemoryCache(),
        trackingCallback: GBTrackingCallback = { _, _ -> },
        configureSdk: GBSDKBuilder.() -> Unit = {},
    ) = GrowthBookOpenFeatureProvider(
        apiKey = "sdk-test",
        apiHost = "https://example.test",
        networkDispatcher = network,
        encryptionKey = encryptionKey,
        attributes = attributes,
        trackingCallback = trackingCallback,
        identityPrecedence = identityPrecedence,
        initializationTimeout = initializationTimeout,
        configureSdk = {
            setCachingLayer(cache)
            configureSdk()
        },
    ).also { providers += it }

    private suspend fun makeClient(
        api: OpenFeatureAPIInstance = createOpenFeatureAPIInstance(),
        network: FakeNetwork,
        attributes: Map<String, GBValue> = emptyMap(),
        encryptionKey: String? = null,
        identityPrecedence: IdentityPrecedence = IdentityPrecedence.TARGETING_KEY,
        context: EvaluationContext? = null,
        initializationTimeout: Duration = 2.seconds,
        cache: MemoryCache = MemoryCache(),
        trackingCallback: GBTrackingCallback = { _, _ -> },
        configureSdk: GBSDKBuilder.() -> Unit = {},
    ): Client {
        val provider = makeProvider(
            network, attributes, encryptionKey, identityPrecedence, initializationTimeout, cache, trackingCallback, configureSdk,
        )
        api.setProviderAndWait(provider, context)
        return api.getClient()
    }
}

/** Network double that serves a fixed response, fails, or never answers. */
class FakeNetwork(@Volatile var response: String?, private val respond: Boolean = true) : NetworkDispatcher {
    override fun consumeGETRequest(request: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit): Job {
        if (respond) {
            response?.let(onSuccess) ?: onError(IllegalStateException("offline"))
        }
        return Job().apply { complete() }
    }

    override fun consumeSSEConnection(url: String, sseController: SSEConnectionController?): Flow<Resource<String>> =
        emptyFlow()

    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        consumeGETRequest(url, onSuccess, onError)
    }
}

/** In-memory cache so tests don't share files. */
class MemoryCache : GBCachingLayer {
    private val contents = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun saveContent(fileName: String, content: String) { contents[fileName] = content }
    override fun getContent(fileName: String): String? = contents[fileName]
}

object Payloads {
    private const val FEATURES_MAP = """
    {
        "bool-flag": {"defaultValue": true},
        "string-flag": {"defaultValue": "hello"},
        "int-flag": {"defaultValue": 42},
        "decimal-flag": {"defaultValue": 0.1},
        "long-flag": {"defaultValue": 5000000000},
        "big-flag": {"defaultValue": 100000000000000000000},
        "json-flag": {"defaultValue": {"list": [1, null, {"nested": "yes"}], "ratio": 0.5}},
        "null-flag": {"defaultValue": null},
        "id-flag": {
            "defaultValue": "default",
            "rules": [{"condition": {"id": "user-1"}, "force": "matched"}]
        },
        "country-flag": {
            "defaultValue": "default",
            "rules": [{"condition": {"country": "CA"}, "force": "ca"}]
        },
        "experiment-flag": {
            "defaultValue": "control",
            "rules": [{
                "key": "experiment-flag",
                "variations": ["control", "treatment"],
                "weights": [0, 1],
                "coverage": 1,
                "hashAttribute": "id"
            }]
        },
        "cyclic-flag": {
            "defaultValue": true,
            "rules": [{"parentConditions": [{"id": "cyclic-flag", "condition": {"value": true}}]}]
        }
    }
    """

    const val features = """{"features": $FEATURES_MAP}"""

    const val ENCRYPTION_KEY = "Ns04T5n9+59rl2x3SlNHtQ=="
    const val encryptedFeatures =
        """{"encryptedFeatures": "RdZCI/ctegPHQ8rPfE/RNA==.9Ry+P4zVJMRnviWuws/0OhorG9mE75CUU2YJRflYeBCA24s41VLG2r9Z7yaAIM86"}"""
}
