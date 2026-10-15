package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.evaluators.GBExperimentHelper
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.evaluators.UserContext
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.StackContext
import com.sdk.growthbook.plugin.GBTrackingEventNames
import com.sdk.growthbook.plugin.tracking.AttributesChangeReceiver
import com.sdk.growthbook.plugin.tracking.CustomEventReceiver
import com.sdk.growthbook.plugin.tracking.FlushablePlugin
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.tests.MockNetworkClient
import com.sdk.growthbook.utils.GBTrackData
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginIntegrationTest {

    private fun buildSdk(
        attributes: Map<String, GBValue> = mapOf("id" to GBString("u1")),
        plugins: List<GrowthBookPlugin> = emptyList(),
    ) = GBSDKBuilder(
        apiKey = "test-key",
        apiHost = "https://test.com",
        attributes = attributes,
        trackingCallback = { _, _ -> },
        networkDispatcher = MockNetworkClient(null, null),
    ).setPlugins(plugins).initialize().also { sdk ->
        // Inject features directly since network is mocked to return nothing.
        sdk.getGBContext().features = hashMapOf(
            "flag-a" to GBFeature(defaultValue = GBBoolean(true)),
            "flag-b" to GBFeature(defaultValue = GBString("x")),
        )
    }

    @Test
    fun pluginsObserveFeatureAndExperimentEvents() {
        val featureSeen = mutableListOf<GBFeatureResult>()
        val experimentSeen = mutableListOf<GBExperimentResult>()
        val closed = AtomicInteger()

        val plugin = object : GrowthBookPlugin {
            override fun onExperimentViewed(experiment: GBExperiment, result: GBExperimentResult, attributes: Map<String, GBValue>?) {
                experimentSeen.add(result)
            }
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                featureSeen.add(result)
            }
            override fun close() { closed.incrementAndGet() }
        }

        val sdk = buildSdk(plugins = listOf(plugin))
        sdk.isOn("flag-a")
        sdk.feature("flag-b")

        val exp = GBExperiment(
            key = "my-exp",
            variations = listOf(GBString("A"), GBString("B")),
        )
        val result = sdk.run(exp)
        sdk.close()

        assertTrue(featureSeen.size >= 2, "plugin should have seen at least 2 feature evaluations")
        if (result.inExperiment) {
            assertEquals(1, experimentSeen.size, "plugin should have seen experiment event exactly once")
        }
        assertEquals(1, closed.get(), "plugin close() should fire when SDK closes")
    }

    @Test
    fun pluginReceivesFeatureEventEvenWithoutExistingCallback() {
        val keys = mutableListOf<String>()
        val plugin = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                keys.add(featureKey)
            }
        }

        val sdk = buildSdk(plugins = listOf(plugin))
        sdk.feature("flag-a")
        sdk.close()

        assertTrue(keys.contains("flag-a"), "plugin should observe flag-a evaluation")
    }

    @Test
    fun pluginInitCalledOnSdkConstruction() {
        val inits = AtomicInteger()
        val plugin = object : GrowthBookPlugin {
            override fun init() { inits.incrementAndGet() }
        }

        buildSdk(plugins = listOf(plugin)).close()

        assertEquals(1, inits.get(), "init() must be called exactly once on construction")
    }

    @Test
    fun multiplePluginsEachReceiveEvents() {
        val firstCalls = AtomicInteger()
        val secondCalls = AtomicInteger()

        val first = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                firstCalls.incrementAndGet()
            }
        }
        val second = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                secondCalls.incrementAndGet()
            }
        }

        val sdk = buildSdk(plugins = listOf(first, second))
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(firstCalls.get(), secondCalls.get(), "both plugins should receive the same number of events")
        assertTrue(firstCalls.get() >= 1)
    }

    @Test
    fun throwingPluginDoesNotBreakEvaluation() {
        val bad = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                throw RuntimeException("plugin error")
            }
        }

        val sdk = buildSdk(plugins = listOf(bad))
        val result = sdk.feature("flag-a")
        sdk.close()

        assertTrue(result.on, "feature evaluation should succeed despite throwing plugin")
    }

    @Test
    fun attributesArePassedToPlugin() {
        val receivedAttributes = mutableListOf<Map<String, GBValue>?>()
        val plugin = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                receivedAttributes.add(attributes)
            }
        }

        val sdk = buildSdk(
            attributes = mapOf("id" to GBString("u1"), "tier" to GBString("gold")),
            plugins = listOf(plugin),
        )
        sdk.feature("flag-a")
        sdk.close()

        assertTrue(receivedAttributes.isNotEmpty())
        val attrs = receivedAttributes.first()
        assertEquals(GBString("u1"), attrs?.get("id"))
        assertEquals(GBString("gold"), attrs?.get("tier"))
    }

    private class RecordingReceiver : GrowthBookPlugin, CustomEventReceiver {
        val events = mutableListOf<Triple<String, Map<String, GBValue>, Map<String, GBValue>?>>()
        override fun onEvent(
            eventName: String,
            properties: Map<String, GBValue>,
            attributes: Map<String, GBValue>?
        ) {
            events.add(Triple(eventName, properties, attributes))
        }
    }

    @Test
    fun logEventReachesPluginsThatOptedIn() {
        val receiver = RecordingReceiver()
        val sdk = buildSdk(
            attributes = mapOf("id" to GBString("u1")),
            plugins = listOf(receiver),
        )

        sdk.logEvent("Checkout Completed", mapOf("plan" to GBString("pro")))
        sdk.close()

        assertEquals(1, receiver.events.size)
        val (name, properties, attributes) = receiver.events.first()
        assertEquals("Checkout Completed", name)
        assertEquals(GBString("pro"), properties["plan"])
        // The instance's current attributes ride along, so the event lands with the same identity
        // as an evaluation made at the same moment.
        assertEquals(GBString("u1"), attributes?.get("id"))
    }

    @Test
    fun logEventSkipsPluginsThatDidNotOptIn() {
        // A plugin that only implements GrowthBookPlugin must not be required to know about
        // custom events at all — that is the point of the separate capability interface.
        val evaluationsOnly = object : GrowthBookPlugin {
            var featureEvents = 0
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                featureEvents++
            }
        }

        val sdk = buildSdk(plugins = listOf(evaluationsOnly))
        sdk.logEvent("Signed Up")
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(1, evaluationsOnly.featureEvents, "only the feature evaluation should register")
    }

    @Test
    fun logEventReachesTheEventLoggerCallback() {
        val seen = mutableListOf<Pair<String, Map<String, GBValue>>>()
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        ).setEventLogger { name, properties, _ -> seen.add(name to properties) }.initialize()

        sdk.logEvent("Signed Up", mapOf("plan" to GBString("free")))
        sdk.close()

        assertEquals(1, seen.size)
        assertEquals("Signed Up", seen.first().first)
        assertEquals(GBString("free"), seen.first().second["plan"])
    }

    @Test
    fun throwingEventLoggerDoesNotPropagate() {
        val receiver = RecordingReceiver()
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        )
            .setEventLogger { _, _, _ -> throw RuntimeException("consumer blew up") }
            .setPlugins(listOf(receiver))
            .initialize()

        sdk.logEvent("Signed Up")
        sdk.close()

        // A failing logger must not take the caller down, nor stop the plugins behind it.
        assertEquals(1, receiver.events.size, "plugins still receive the event")
    }

    private class RecordedEvent(
        val name: String,
        val properties: Map<String, GBValue>,
        val attributes: Map<String, GBValue>?,
    )

    private fun buildSdkWithLogger(
        sink: MutableList<RecordedEvent>,
        attributes: Map<String, GBValue> = mapOf("id" to GBString("u1")),
    ) = GBSDKBuilder(
        apiKey = "test-key",
        apiHost = "https://test.com",
        attributes = attributes,
        trackingCallback = { _, _ -> },
        networkDispatcher = MockNetworkClient(null, null),
    ).setEventLogger { name, properties, attrs ->
        sink.add(RecordedEvent(name, properties, attrs))
    }.initialize().also { sdk ->
        sdk.getGBContext().features = hashMapOf(
            "flag-a" to GBFeature(defaultValue = GBBoolean(true)),
            "flag-b" to GBFeature(defaultValue = GBString("x")),
        )
    }

    @Test
    fun eventLoggerReceivesFeatureEvaluatedWithTheCrossSdkPropertyShape() {
        val seen = mutableListOf<RecordedEvent>()
        val sdk = buildSdkWithLogger(seen)

        sdk.feature("flag-b")
        sdk.close()

        val event = seen.single { it.name == GBTrackingEventNames.FEATURE_EVALUATED }
        // Same keys and values the JS/Java SDKs emit — a warehouse query must not need a
        // per-SDK mapping.
        assertEquals(GBString("flag-b"), event.properties["feature"])
        assertEquals(GBString("defaultValue"), event.properties["source"])
        assertEquals(GBString("x"), event.properties["value"])
        assertEquals(GBString("\$default"), event.properties["ruleId"])
        assertEquals(GBString(""), event.properties["variationId"])
        assertEquals(GBString("u1"), event.attributes?.get("id"))
    }

    @Test
    fun eventLoggerReceivesExperimentViewed() {
        val seen = mutableListOf<RecordedEvent>()
        val sdk = buildSdkWithLogger(seen)

        val result = sdk.run(
            GBExperiment(
                key = "my-exp",
                variations = listOf(GBString("A"), GBString("B")),
            )
        )
        sdk.close()

        val exposures = seen.filter { it.name == GBTrackingEventNames.EXPERIMENT_VIEWED }
        if (result.inExperiment) {
            val event = exposures.single()
            assertEquals(GBString("my-exp"), event.properties["experimentId"])
            assertEquals(GBString(result.key), event.properties["variationId"])
            assertEquals(GBString("id"), event.properties["hashAttribute"])
            assertEquals(GBString("u1"), event.properties["hashValue"])
        } else {
            assertTrue(exposures.isEmpty(), "no exposure event without enrollment")
        }
    }

    @Test
    fun eventLoggerAndPluginsBothReceiveTheSameEvaluationEvents() {
        // The sink is additive: registering one must not cost a plugin its callbacks, which is
        // exactly what a single-slot logger (as in the JS SDK) would do.
        val seen = mutableListOf<RecordedEvent>()
        val pluginKeys = mutableListOf<String>()
        val plugin = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                pluginKeys.add(featureKey)
            }
        }

        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        )
            .setEventLogger { name, properties, attrs -> seen.add(RecordedEvent(name, properties, attrs)) }
            .setPlugins(listOf(plugin))
            .initialize()
        sdk.getGBContext().features = hashMapOf("flag-a" to GBFeature(defaultValue = GBBoolean(true)))

        sdk.feature("flag-a")
        sdk.close()

        assertEquals(listOf("flag-a"), pluginKeys)
        assertEquals(1, seen.count { it.name == GBTrackingEventNames.FEATURE_EVALUATED })
    }

    @Test
    fun forcedFeatureIsReportedToNoUsageSink() {
        // A forced feature is a dev/QA override, not an exposure: the reference SDK skips the whole
        // usage fan-out for source == "override" (core.ts). All three sinks must stay silent here,
        // while the value itself is still served.
        val seen = mutableListOf<RecordedEvent>()
        val pluginKeys = mutableListOf<String>()
        val usageKeys = mutableListOf<String>()
        val plugin = object : GrowthBookPlugin {
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                pluginKeys.add(featureKey)
            }
        }

        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        )
            .setEventLogger { name, properties, attrs -> seen.add(RecordedEvent(name, properties, attrs)) }
            .setFeatureUsageCallback { key, _ -> usageKeys.add(key) }
            .setPlugins(listOf(plugin))
            .initialize()
        sdk.getGBContext().features = hashMapOf(
            "flag-a" to GBFeature(defaultValue = GBBoolean(true)),
            "flag-b" to GBFeature(defaultValue = GBString("x")),
        )
        sdk.setForcedFeatures(mapOf("flag-a" to GBBoolean(false)))

        val forced = sdk.feature("flag-a")

        assertEquals(GBFeatureSource.override, forced.source)
        assertEquals(GBBoolean(false), forced.gbValue, "the forced value is still served")
        assertTrue(pluginKeys.isEmpty(), "plugins must not observe a forced feature")
        assertTrue(usageKeys.isEmpty(), "featureUsageCallback must not fire for a forced feature")
        assertTrue(
            seen.none { it.name == GBTrackingEventNames.FEATURE_EVALUATED },
            "no Feature Evaluated event may reach the ingest path for a forced feature"
        )

        // Control: an ordinary evaluation on the same instance still reports through all three.
        sdk.feature("flag-b")
        sdk.close()

        assertEquals(listOf("flag-b"), pluginKeys)
        assertEquals(listOf("flag-b"), usageKeys)
        assertEquals(1, seen.count { it.name == GBTrackingEventNames.FEATURE_EVALUATED })
    }

    /** Wires all three usage sinks to one recorder, since they are gated together. */
    private class UsageSinks {
        val plugin = mutableListOf<GBValue?>()
        val callback = mutableListOf<GBValue?>()
        val logger = mutableListOf<GBValue>()

        fun build(attributes: Map<String, GBValue> = mapOf("id" to GBString("u1"))) =
            GBSDKBuilder(
                apiKey = "test-key",
                apiHost = "https://test.com",
                attributes = attributes,
                trackingCallback = { _, _ -> },
                networkDispatcher = MockNetworkClient(null, null),
            )
                .setPlugins(
                    listOf(object : GrowthBookPlugin {
                        override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                            plugin.add(result.gbValue)
                        }
                    })
                )
                .setFeatureUsageCallback { _, result -> callback.add(result.gbValue) }
                .setEventLogger { name, properties, _ ->
                    if (name == GBTrackingEventNames.FEATURE_EVALUATED) {
                        properties["value"]?.let { logger.add(it) }
                    }
                }
                .initialize()
                .also { sdk ->
                    sdk.getGBContext().features =
                        hashMapOf("flag-a" to GBFeature(defaultValue = GBString("a")))
                }
    }

    @Test
    fun repeatedEvaluationOfTheSameValueReportsUsageOnce() {
        val sinks = UsageSinks()
        val sdk = sinks.build()

        repeat(3) { sdk.feature("flag-a") }
        sdk.close()

        assertEquals(1, sinks.callback.size, "featureUsageCallback fires on a value change only")
        assertEquals(1, sinks.plugin.size, "plugins are gated by the same de-duplication")
        assertEquals(1, sinks.logger.size, "so is the event logger, and the ingest path behind it")
    }

    @Test
    fun throwingUsageCallbackDoesNotCostThePluginsTheValue() {
        // The value change is consumed once, so a throwing consumer callback must not take the
        // other sinks down with it: they would never hear about this value again, not just miss
        // this one call.
        val pluginValues = mutableListOf<GBValue?>()
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        )
            .setFeatureUsageCallback { _, _ -> throw RuntimeException("consumer blew up") }
            .setPlugins(
                listOf(object : GrowthBookPlugin {
                    override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                        pluginValues.add(result.gbValue)
                    }
                })
            )
            .initialize()
        sdk.getGBContext().features = hashMapOf("flag-a" to GBFeature(defaultValue = GBString("a")))

        val result = sdk.feature("flag-a")
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(GBString("a"), result.gbValue, "evaluation survives a throwing sink")
        assertEquals(listOf<GBValue?>(GBString("a")), pluginValues)
    }

    @Test
    fun throwingTrackingCallbackDoesNotCostPluginsARemoteEvalExposure() {
        // The sibling of the test above, for the rule.tracks path a remote-eval payload takes.
        // gbExperimentHelper.isTracked() marks the exposure before the callbacks run, so it is
        // offered exactly once: a throwing trackingCallback sharing a try with the fan-out would
        // lose it from the plugin and the event logger permanently, not just for this call.
        val pluginExposures = mutableListOf<String>()
        val loggedExposures = mutableListOf<String>()
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> throw RuntimeException("consumer blew up") },
            networkDispatcher = MockNetworkClient(null, null),
        )
            .setEventLogger { eventName, properties, _ ->
                if (eventName == GBTrackingEventNames.EXPERIMENT_VIEWED) {
                    loggedExposures.add((properties["experimentId"] as GBString).value)
                }
            }
            .setPlugins(
                listOf(object : GrowthBookPlugin {
                    override fun onExperimentViewed(experiment: GBExperiment, result: GBExperimentResult, attributes: Map<String, GBValue>?) {
                        pluginExposures.add(experiment.key)
                    }
                })
            )
            .initialize()
        sdk.getGBContext().features = hashMapOf(
            "remote-flag" to GBFeature(
                rules = listOf(
                    GBFeatureRule(
                        id = "remote-rule",
                        force = GBString("forced"),
                        tracks = arrayListOf(
                            GBTrackData(
                                experiment = GBExperiment(
                                    key = "remote-exp",
                                    variations = listOf(GBString("A"), GBString("B")),
                                ),
                                result = GBExperimentResult(
                                    inExperiment = true,
                                    variationId = 1,
                                    value = GBString("B"),
                                    hashAttribute = "id",
                                    hashValue = "u1",
                                ),
                            )
                        ),
                    )
                )
            )
        )

        val result = sdk.feature("remote-flag")
        sdk.close()

        assertEquals(GBString("forced"), result.gbValue, "evaluation survives a throwing callback")
        assertEquals(listOf("remote-exp"), pluginExposures, "plugins still see the exposure")
        assertEquals(listOf("remote-exp"), loggedExposures, "so does the event logger")
    }

    @Test
    fun changedValueReportsUsageAgain() {
        val sinks = UsageSinks()
        val sdk = sinks.build()

        sdk.feature("flag-a")
        sdk.getGBContext().features = hashMapOf("flag-a" to GBFeature(defaultValue = GBString("b")))
        sdk.feature("flag-a")
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(listOf<GBValue?>(GBString("a"), GBString("b")), sinks.callback)
        assertEquals(listOf<GBValue?>(GBString("a"), GBString("b")), sinks.plugin)
    }

    @Test
    fun userChangeReportsUsageAgainForTheSameValue() {
        // The next user's first read is a fresh report even though the value did not move. The
        // reference SDK keeps its map across attribute changes and loses this one.
        val sinks = UsageSinks()
        val sdk = sinks.build()

        sdk.feature("flag-a")
        sdk.setAttributes(mapOf("id" to GBString("u2")))
        sdk.feature("flag-a")
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(listOf<GBValue?>(GBString("a"), GBString("a")), sinks.callback)
        assertEquals(2, sinks.logger.size)
    }

    private class RecordingAttributesReceiver :
        GrowthBookPlugin, AttributesChangeReceiver {
        val snapshots = mutableListOf<Map<String, GBValue>>()
        override fun onAttributesChanged(attributes: Map<String, GBValue>) {
            snapshots.add(attributes)
        }
    }

    @Test
    fun attributeChangesReachPluginsThatOptedIn() {
        val receiver = RecordingAttributesReceiver()
        val sdk = buildSdk(plugins = listOf(receiver))

        sdk.setAttributes(mapOf("id" to GBString("u2")))
        assertEquals(1, receiver.snapshots.size)
        assertEquals(GBString("u2"), receiver.snapshots.last()["id"])

        // Re-setting the same map is not a user change. Attribute setters are called on every
        // screen in a typical app, and a plugin resetting per-user state each time would undo
        // de-duplication it was right to apply.
        sdk.setAttributes(mapOf("id" to GBString("u2")))
        assertEquals(1, receiver.snapshots.size, "an unchanged map must not fire")

        sdk.updateAttributes(mapOf("plan" to GBString("pro")))
        assertEquals(2, receiver.snapshots.size, "a merge that changes the map fires")
        assertEquals(GBString("u2"), receiver.snapshots.last()["id"], "merge keeps untouched keys")
        assertEquals(GBString("pro"), receiver.snapshots.last()["plan"])

        // Overrides adjust sticky-bucket identity for the same user — not a user change.
        sdk.setAttributeOverrides(mapOf("id" to GBString("u3")))
        assertEquals(2, receiver.snapshots.size, "attribute overrides must not fire")

        sdk.close()
    }

    @Test
    fun attributeChangesSkipPluginsThatDidNotOptIn() {
        val evaluationsOnly = object : GrowthBookPlugin {
            var featureEvents = 0
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                featureEvents++
            }
        }

        val sdk = buildSdk(plugins = listOf(evaluationsOnly))
        sdk.setAttributes(mapOf("id" to GBString("u2")))
        sdk.feature("flag-a")
        sdk.close()

        assertEquals(1, evaluationsOnly.featureEvents, "only the feature evaluation should register")
    }

    @Test
    fun throwingAttributesReceiverDoesNotBreakTheSetter() {
        val exploding = object : GrowthBookPlugin, AttributesChangeReceiver {
            override fun onAttributesChanged(attributes: Map<String, GBValue>) {
                throw RuntimeException("plugin blew up")
            }
        }
        val healthy = RecordingAttributesReceiver()

        val sdk = buildSdk(plugins = listOf(exploding, healthy))
        sdk.setAttributes(mapOf("id" to GBString("u2")))

        assertEquals(1, healthy.snapshots.size, "a failing plugin must not suppress the others")
        assertEquals(GBString("u2"), sdk.getGBContext().evalSnapshot().attributes["id"])

        sdk.close()
    }

    @Test
    fun throwingEventLoggerDoesNotBreakEvaluation() {
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("u1")),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
        ).setEventLogger { _, _, _ -> throw RuntimeException("consumer blew up") }.initialize()
        sdk.getGBContext().features = hashMapOf("flag-a" to GBFeature(defaultValue = GBBoolean(true)))

        val result = sdk.feature("flag-a")
        sdk.close()

        assertTrue(result.on, "evaluation must survive a throwing sink")
    }

    private class RecordingFlushable(private val fail: Boolean = false) :
        GrowthBookPlugin, FlushablePlugin {
        val flushes = AtomicInteger()
        override suspend fun flush() {
            flushes.incrementAndGet()
            if (fail) throw RuntimeException("flush blew up")
        }
    }

    @Test
    fun sdkFlushDrainsEveryPluginThatOptedIn() = runBlocking {
        // The point of the fan-out: a consumer who registered plugins through setPlugins(...) can
        // drain them without having kept a reference to each one.
        val first = RecordingFlushable()
        val second = RecordingFlushable()
        val sdk = buildSdk(plugins = listOf(first, second))

        sdk.flush()
        sdk.flush()
        sdk.close()

        assertEquals(2, first.flushes.get())
        assertEquals(2, second.flushes.get())
    }

    @Test
    fun sdkFlushSkipsPluginsThatDidNotOptIn() = runBlocking {
        // A plugin with no buffer must not be dragged into the drain at all.
        val plain = object : GrowthBookPlugin {
            val calls = AtomicInteger()
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) {
                calls.incrementAndGet()
            }
        }
        val flushable = RecordingFlushable()
        val sdk = buildSdk(plugins = listOf(plain, flushable))

        sdk.flush()
        sdk.close()

        assertEquals(0, plain.calls.get(), "flush must not reach the evaluation callbacks")
        assertEquals(1, flushable.flushes.get())
    }

    @Test
    fun aThrowingFlushDoesNotCostTheOtherPluginsTheirs() = runBlocking {
        // Same isolation the other fan-outs give: one plugin failing to send its buffer must not
        // strand everyone else's.
        val throwing = RecordingFlushable(fail = true)
        val healthy = RecordingFlushable()
        val sdk = buildSdk(plugins = listOf(throwing, healthy))

        sdk.flush()
        sdk.close()

        assertEquals(1, throwing.flushes.get())
        assertEquals(1, healthy.flushes.get(), "the throwing plugin must not block the next one")
    }

    @Test
    fun aCancelledFlushPropagatesRatherThanBeingSwallowed() = runBlocking {
        // Unlike the non-suspending fan-outs, this one can be cancelled with its caller. Treating
        // that as a plugin failure would leave the caller's coroutine running past its cancel.
        val cancelling = object : GrowthBookPlugin, FlushablePlugin {
            override suspend fun flush() = throw CancellationException("caller went away")
        }
        val later = RecordingFlushable()
        val sdk = buildSdk(plugins = listOf(cancelling, later))

        assertFailsWith<CancellationException> { sdk.flush() }
        assertEquals(0, later.flushes.get(), "cancellation stops the fan-out")
        sdk.close()
    }

    @Test
    fun aStaleEvaluationDoesNotSuppressTheNextUsersFirstReport() {
        // The interleaving a slow evaluation creates: it reads its snapshot before the user
        // changes and reaches the usage gate after. Driving the evaluator with the snapshot
        // captured beforehand reproduces it deterministically — that snapshot is exactly what
        // such an evaluation still holds.
        //
        // The late report belongs to the previous user and is made, correctly, against the
        // previous user's history. What must not happen is it landing in the *current* user's
        // history, where it would suppress their first read of the same value for as long as the
        // value stayed put.
        val sinks = UsageSinks()
        val sdk = sinks.build()

        // The previous user's evaluation starts here.
        val staleSnapshot = sdk.getGBContext().evalSnapshot()

        sdk.setAttributes(mapOf("id" to GBString("u2")))

        // ...and only now reaches getFeatureResult.
        val staleReports = mutableListOf<GBValue?>()
        GBFeatureEvaluator(
            EvaluationContext(
                enabled = true,
                features = staleSnapshot.features,
                savedGroups = staleSnapshot.savedGroups,
                forcedVariations = staleSnapshot.forcedVariations,
                trackingCallback = { _, _ -> },
                gbExperimentHelper = GBExperimentHelper(),
                gbFeatureUsageHelper = staleSnapshot.featureUsageHelper,
                stickyBucketService = null,
                loggingEnabled = false,
                onFeatureUsage = { _, result -> staleReports.add(result.gbValue) },
                userContext = UserContext(
                    qaMode = false,
                    attributes = staleSnapshot.attributes,
                    stickyBucketAssignmentDocs = staleSnapshot.stickyBucketAssignmentDocs,
                ),
                stackContext = StackContext(null, mutableSetOf()),
                pluginRegistry = null,
            )
        ).evaluateFeature("flag-a", staleSnapshot.attributeOverrides)

        sdk.feature("flag-a")
        sdk.close()

        assertEquals(
            listOf<GBValue?>(GBString("a")),
            staleReports,
            "the previous user's evaluation still reports",
        )
        assertEquals(
            listOf<GBValue?>(GBString("a")),
            sinks.callback,
            "and does not cost the current user their first report of that value",
        )
    }
}
