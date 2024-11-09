package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult

internal class GBExperimentHelper {

    private var trackedExperiments: MutableSet<String> = mutableSetOf()

    fun isTracked(experiment: GBExperiment, result: GBExperimentResult?): Boolean {
        val experimentKey = experiment.key

        //Make sure a tracking callback is only fired once per unique experiment
        val key = (result?.hashAttribute ?: "") +
        (result?.hashValue ?: "") +
        (experimentKey + result?.variationId)
        if (trackedExperiments.contains(key)) { return true }
        trackedExperiments.add(key)
        return false
    }
}
