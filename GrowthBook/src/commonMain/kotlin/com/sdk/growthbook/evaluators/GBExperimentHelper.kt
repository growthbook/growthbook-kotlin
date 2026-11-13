package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Remembers which exposures have already been announced, so a tracking callback fires once per
 * unique experiment. The counterpart of [GBFeatureUsageHelper] for experiment exposures.
 *
 * The key carries `hashAttribute`/`hashValue` next to the experiment key and variation (as
 * `getExperimentDedupeKey` does in core.ts), so this history tells users apart by itself. That is
 * why one instance lives for the lifetime of the SDK and is deliberately *not* on the
 * [com.sdk.growthbook.model.EvalSnapshot]: handing it a fresh instance on a user change would
 * re-fire an exposure it correctly remembered across an A -> B -> A switch.
 *
 * Allocating a new set per tracked exposure is deliberate. Exposures are orders of magnitude rarer
 * than evaluations — the same argument that justified the copy-on-write map in
 * [GBFeatureUsageHelper] — so an evaluation that tracks nothing pays only an atomic load.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class GBExperimentHelper {

    private val trackedExperiments = AtomicReference<Set<String>>(emptySet())

    /**
     * Reports whether this exposure was already announced, marking it as announced if it was not.
     *
     * The test-and-set runs in a CAS loop, so two threads evaluating the same experiment at the
     * same time cannot both be told the exposure is new: the loser re-reads the winner's set and
     * finds the key there. `feature()`/`run()` are callable from any thread, and a plain set let
     * both pass the membership check before either recorded the key — one exposure, two rows in
     * the warehouse.
     */
    fun isTracked(experiment: GBExperiment, result: GBExperimentResult?): Boolean {
        //Make sure a tracking callback is only fired once per unique experiment
        val key = (result?.hashAttribute ?: "") +
            (result?.hashValue ?: "") +
            (experiment.key + result?.variationId)
        while (true) {
            val tracked = trackedExperiments.load()
            if (key in tracked) {
                return true
            }
            if (trackedExperiments.compareAndSet(tracked, tracked + key)) {
                return false
            }
        }
    }

    /**
     * Releases the exposure history, as the reference SDK's `destroy()` does
     * (`_trackedExperiments.clear()`, GrowthBook.ts).
     *
     * A memory release, not a semantic one: the instance is unusable after
     * [com.sdk.growthbook.GrowthBookSDK.close]. Safe while an evaluation is in flight only because
     * the set is swapped behind the atomic reference rather than cleared in place.
     */
    fun reset() {
        trackedExperiments.store(emptySet())
    }
}
