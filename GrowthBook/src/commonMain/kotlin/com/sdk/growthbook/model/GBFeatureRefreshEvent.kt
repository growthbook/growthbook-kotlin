package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures

/**
 * One attempt to refresh the feature definitions, delivered to every listener registered through
 * `GrowthBookSDK.addFeatureRefreshListener`.
 *
 * Two independent axes: [outcome] says what this attempt did, [definitionsSource] says where the
 * definitions evaluation now runs against came from — the same [GBDefinitionsOrigin] that
 * `GrowthBookSDK.getFeaturesStatus()` reports. [definitionsSource] and [features] are read from one
 * snapshot, after the attempt was applied, so they always describe the same state.
 *
 * Reported on no guaranteed thread. Usually it is the SDK's payload-processing dispatcher (the
 * platform IO dispatcher by default), so marshal back to your UI thread yourself if the listener
 * touches UI state — but a cache read is delivered synchronously on the caller's thread and an SSE
 * failure on the collecting dispatcher, so a listener must never block either.
 *
 * Exactly one event is emitted per refresh attempt, including the cache load that precedes the
 * first network round. That is the difference from the handler set via
 * `GBSDKBuilder.setRefreshHandler`, which reports remote results only: a listener also hears the
 * definitions evaluation actually starts the session with. The one exception is a cache payload
 * that is not applied because network definitions are already loaded (the pre-load in front of a
 * later refresh): it changes nothing and is not a refresh result, so it raises no event — the
 * network round that follows does.
 */
@ConsistentCopyVisibility
data class GBFeatureRefreshEvent internal constructor(
    /** What this refresh attempt did. */
    val outcome: GBFeatureRefreshOutcome,
    /**
     * Origin of the definitions in effect after this attempt — unchanged by a [GBFeatureRefreshOutcome.NotModified]
     * or a [GBFeatureRefreshOutcome.Failed] attempt, except for the stale-if-error fallback, which
     * reports [GBDefinitionsOrigin.Stale]. Branch an "outdated data" state on that value.
     */
    val definitionsSource: GBDefinitionsOrigin,
    /**
     * The definitions in effect after this attempt: the ones it applied, or the ones still in
     * effect when it applied none — a 304, a failure, or a payload carrying only saved groups.
     * Handed over as-is rather than copied. Read it; treat it as immutable.
     */
    val features: GBFeatures,
    /**
     * Why the refresh failed. Non-null exactly when [outcome] is [GBFeatureRefreshOutcome.Failed];
     * for the stale-if-error fallback it is the network error that triggered it.
     */
    val error: GBError? = null
)

/**
 * What a refresh attempt did, independently of where the definitions in effect came from (that is
 * [GBFeatureRefreshEvent.definitionsSource]).
 *
 * Will only gain values in a major release: a new constant breaks an exhaustive `when` in consumer
 * code.
 */
enum class GBFeatureRefreshOutcome {
    /**
     * New definitions were applied — from the network (a features request, a remote evaluation or
     * a streamed update) or from the cache. A payload carrying only saved groups or bandit
     * definitions counts too; [GBFeatureRefreshEvent.features] is then unchanged.
     */
    Updated,

    /**
     * The server answered 304 Not Modified while definitions were loaded: what is in effect is
     * still current, and [GBFeatureRefreshEvent.features] is unchanged.
     */
    NotModified,

    /**
     * The attempt failed: a network or cache read error, a 304 that arrived before any
     * definitions were loaded, or the network failure behind the stale-if-error fallback
     * (`GBSDKBuilder.setServeStaleOnError`), in which case an expired cache is now in effect and
     * [GBFeatureRefreshEvent.definitionsSource] is [GBDefinitionsOrigin.Stale].
     * [GBFeatureRefreshEvent.error] says what went wrong.
     */
    Failed,
}
