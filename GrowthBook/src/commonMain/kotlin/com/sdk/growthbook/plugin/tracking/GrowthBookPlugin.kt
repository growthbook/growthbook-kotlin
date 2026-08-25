package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBValue

/**
 * A plugin that can observe experiment and feature evaluations on a GrowthBook instance.
 *
 * Implementations MUST be thread-safe. All methods default to no-ops so custom plugins only
 * override what they need. Methods should return quickly (enqueue work, don't block evaluation).
 * Exceptions thrown by these methods are caught by [PluginRegistry] so one failing plugin
 * can't break evaluation or other plugins.
 */
interface GrowthBookPlugin {
    /**
     * Called once when the plugin is registered with a GrowthBook instance.
     * If this throws, the plugin is treated as failed and the other methods
     * still need to remain safe to call (no-op is acceptable).
     */
    fun init() {}

    /**
     * Invoked after a user is bucketed into an experiment (once per
     * unique hashAttribute/hashValue/experiment.key/variation combination).
     */
    fun onExperimentViewed(
        experiment: GBExperiment,
        result: GBExperimentResult,
        attributes: Map<String, GBValue>? = null
    ) {
    }

    /**
     * Invoked every time a feature is evaluated.
     */
    fun onFeatureEvaluated(
        featureKey: String,
        result: GBFeatureResult,
        attributes: Map<String, GBValue>? = null
    ) {
    }

    /**
     * Invoked when the owning GrowthBook instance is closed. Implementations should flush any
     * buffered work. MUST be safe to call multiple times and even if [init] failed.
     */
    fun close() {}
}
