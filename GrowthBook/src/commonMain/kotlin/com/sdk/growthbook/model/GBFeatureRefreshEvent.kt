package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures

/**
 * One attempt to refresh the feature definitions, delivered to every listener registered through
 * `GrowthBookSDK.addFeatureRefreshListener`.
 *
 * Reported on no guaranteed thread. Usually it is the SDK's payload-processing dispatcher (the
 * platform IO dispatcher by default), so marshal back to your UI thread yourself if the listener
 * touches UI state — but a cache read is delivered synchronously on the caller's thread and an SSE
 * failure on the collecting dispatcher, so a listener must never block either.
 *
 * Exactly one event is emitted per refresh, including the cache pre-load that precedes the first
 * network round. That is the difference from the handler set via `GBSDKBuilder.setRefreshHandler`,
 * which reports remote results only: a listener also hears the definitions evaluation actually
 * starts the session with.
 */
@ConsistentCopyVisibility
data class GBFeatureRefreshEvent internal constructor(
    /**
     * Whether the refresh itself succeeded. False for a failed fetch, and for the
     * [GBFeatureRefreshSource.Stale] fallback — where definitions *were* applied, but only because
     * the network round failed. Either way [error] describes what went wrong and [features] holds
     * what evaluation is running against, so this is the flag to branch an "outdated data" state
     * on.
     */
    val success: Boolean,
    /**
     * Where the definitions came from, and the only way to tell a cache pre-load, a 304 and a
     * stale fallback apart — [success] alone collapses all three.
     */
    val source: GBFeatureRefreshSource,
    /**
     * The definitions this refresh applied, or the ones still in effect when it applied none — a
     * 304, a failure, or a payload carrying only saved groups. Handed over as-is rather than
     * copied. Read it; treat it as immutable.
     */
    val features: GBFeatures,
    /**
     * Why the refresh failed, when it did. Null whenever [success] is true; non-null for a failed
     * fetch and for the stale fallback, where it is the network error that triggered it.
     */
    val error: GBError? = null
)

/**
 * Where the definitions a [GBFeatureRefreshEvent] reports came from.
 *
 * [NotModified] is an outcome rather than a source, and sits in this enum deliberately: mixing the
 * two axes keeps the event a single `when` at the call site, which is what a consumer branching on
 * "what just happened" actually wants.
 *
 * Unrelated to [GBFeatureSource], which describes where a single feature *value* came from
 * (default / force / experiment).
 */
enum class GBFeatureRefreshSource {
    /**
     * Fetched from the API — a features request, a remote evaluation, or a streamed update.
     * These are indistinguishable at this layer, so all three arrive as [Network].
     */
    Network,

    /**
     * Read from the local cache, which happens on startup and before the first network round of
     * a session.
     */
    Cache,

    /**
     * The server answered 304 Not Modified: the cached definitions are still current, so
     * [GBFeatureRefreshEvent.features] is unchanged.
     */
    NotModified,

    /**
     * An expired cache served as a last resort because the revalidating network round failed —
     * the stale-if-error fallback enabled by `GBSDKBuilder.setServeStaleOnError`. The definitions
     * in [GBFeatureRefreshEvent.features] are in effect but past their freshness ceiling, and
     * [GBFeatureRefreshEvent.error] carries the failure that caused the fallback, so the event
     * reports `success = false`: something is being served, but the refresh did not succeed.
     */
    Stale,
}
