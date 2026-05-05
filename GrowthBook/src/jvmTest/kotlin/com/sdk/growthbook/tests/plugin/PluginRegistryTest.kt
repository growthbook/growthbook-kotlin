package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.plugin.tracking.PluginRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginRegistryTest {

    private fun experiment(key: String) = GBExperiment(key = key)
    private fun experimentResult() = GBExperimentResult(value = GBNull)
    private fun featureResult() = GBFeatureResult(gbValue = GBString("v"), source = GBFeatureSource.defaultValue)

    @Test
    fun dispatchesToEachPlugin() {
        val exp = AtomicInteger()
        val feat = AtomicInteger()
        val inits = AtomicInteger()
        val closes = AtomicInteger()

        fun makePlugin(): GrowthBookPlugin = object : GrowthBookPlugin {
            override fun init() { inits.incrementAndGet() }
            override fun onExperimentViewed(experiment: GBExperiment, result: GBExperimentResult, attributes: Map<String, GBValue>?) { exp.incrementAndGet() }
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) { feat.incrementAndGet() }
            override fun close() { closes.incrementAndGet() }
        }

        val registry = PluginRegistry(listOf(makePlugin(), makePlugin()))
        registry.initAll()
        registry.fireExperimentViewed(experiment("e"), experimentResult())
        registry.fireFeatureEvaluated("f", featureResult())
        registry.closeAll()

        assertEquals(2, inits.get())
        assertEquals(2, exp.get())
        assertEquals(2, feat.get())
        assertEquals(2, closes.get())
    }

    @Test
    fun onePluginThrowingDoesNotStopOthers() {
        val calls = AtomicInteger()
        val bad = object : GrowthBookPlugin {
            override fun init() { throw RuntimeException("boom") }
            override fun onExperimentViewed(experiment: GBExperiment, result: GBExperimentResult, attributes: Map<String, GBValue>?) { throw RuntimeException("boom") }
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) { throw RuntimeException("boom") }
            override fun close() { throw RuntimeException("boom") }
        }
        val good = object : GrowthBookPlugin {
            override fun init() { calls.incrementAndGet() }
            override fun onExperimentViewed(experiment: GBExperiment, result: GBExperimentResult, attributes: Map<String, GBValue>?) { calls.incrementAndGet() }
            override fun onFeatureEvaluated(featureKey: String, result: GBFeatureResult, attributes: Map<String, GBValue>?) { calls.incrementAndGet() }
            override fun close() { calls.incrementAndGet() }
        }

        val registry = PluginRegistry(listOf(bad, good))
        registry.initAll()
        registry.fireExperimentViewed(experiment("e"), experimentResult())
        registry.fireFeatureEvaluated("f", featureResult())
        registry.closeAll()

        assertEquals(4, calls.get(), "good plugin should receive all 4 lifecycle events")
    }

    @Test
    fun emptyRegistryIsNoOp() {
        val registry = PluginRegistry(emptyList())
        registry.initAll()
        registry.fireExperimentViewed(experiment("e"), experimentResult())
        registry.fireFeatureEvaluated("f", featureResult())
        registry.closeAll()
    }

    @Test
    fun nullPluginListIsNoOp() {
        val registry = PluginRegistry(null)
        registry.initAll()
        registry.closeAll()
    }
}
