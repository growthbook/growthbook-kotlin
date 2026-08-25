package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.tests.MockNetworkClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
