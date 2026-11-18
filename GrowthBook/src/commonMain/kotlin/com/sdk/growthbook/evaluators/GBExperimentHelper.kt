package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult

internal class GBExperimentHelper {

    private var trackedExperiments: MutableSet<String> = mutableSetOf()

    /**
     * Whether this exposure has already been tracked — and, if not, **records it as tracked**.
     * Despite the name this is not a pure query: it is a test-and-set, and the caller fires the
     * tracking callback exactly when it returns false.
     *
     * The identity is the hash attribute, its value, the experiment key and the assigned variation
     * together, so a user re-bucketed into another variation is tracked again, while repeated
     * evaluations of an unchanged assignment are not.
     */
    fun isTracked(experiment: GBExperiment, result: GBExperimentResult?): Boolean {
        val experimentKey = experiment.key

        //Make sure a tracking callback is only fired once per unique experiment
        val key = (result?.hashAttribute ?: "") +
            (result?.hashValue ?: "") +
            (experimentKey + result?.variationId)
        if (trackedExperiments.contains(key)) {
            return true
        }
        trackedExperiments.add(key)
        return false
    }
}
