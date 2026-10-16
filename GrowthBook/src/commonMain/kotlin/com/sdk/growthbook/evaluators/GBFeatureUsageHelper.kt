package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBValue
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Remembers the value last reported for each feature, so usage is announced on a *change* rather
 * than on every evaluation. The counterpart of [GBExperimentHelper] for feature usage.
 *
 * A feature is typically read on every render or recomposition, which makes the raw evaluation
 * stream orders of magnitude larger than the number of value changes in it. The reference SDK
 * de-duplicates at this level (`trackedFeatureUsage` in core.ts) and gates every usage sink behind
 * it, so a consumer callback, a plugin and the ingest endpoint all see one report per distinct
 * value.
 *
 * Comparison is by [GBValue] equality rather than by serialized form. That is one deliberate
 * deviation from `JSON.stringify`: [com.sdk.growthbook.model.GBNumber] treats integer and
 * floating-point values distinctly, so a feature whose value flips between `1` and `1.0` reports
 * twice here and once there. Serializing on every evaluation to match would allocate on the hot
 * path for a case that cannot change what the value *means*.
 *
 * Bounded by the number of distinct feature keys evaluated, like the reference implementation.
 *
 * There is deliberately no `reset()`. The history belongs to one user, and a user change publishes
 * a whole new instance on the [com.sdk.growthbook.model.EvalSnapshot] instead of emptying this one
 * — see [com.sdk.growthbook.model.EvalSnapshot.featureUsageHelper] for why clearing in place let a
 * slow evaluation suppress the next user's first report.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class GBFeatureUsageHelper {

    private val lastReported = AtomicReference<Map<String, GBValue?>>(emptyMap())

    /**
     * Records [value] as the latest for [featureKey] and reports whether it differs from the
     * previously recorded one — i.e. whether this evaluation is worth announcing.
     *
     * A key that was never recorded always reports, including when its value is null: absent and
     * "evaluated to null" are different states, which is why membership is checked rather than
     * relying on a null lookup.
     *
     * The read-compare-write runs in a CAS loop, so two threads evaluating the same feature at the
     * same time cannot both be told to report: the loser re-reads the winner's value and sees no
     * change.
     */
    fun shouldReport(featureKey: String, value: GBValue?): Boolean {
        while (true) {
            val current = lastReported.load()
            if (current.containsKey(featureKey) && current[featureKey] == value) {
                return false
            }
            if (lastReported.compareAndSet(current, current + (featureKey to value))) {
                return true
            }
        }
    }
}
