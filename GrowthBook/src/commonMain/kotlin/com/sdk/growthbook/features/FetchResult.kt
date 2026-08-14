package com.sdk.growthbook.features

/**
 * Outcome of a single coalesced network refresh round, returned by
 * [FeaturesViewModel.awaitRefresh]. Lets a suspending caller (e.g.
 * suspendFeature()) observe how the shared in-flight refresh completed
 * without polling delegate state.
 */
internal enum class FetchResult {
    Success,
    Failed,
    NotModified,

    /**
     * The remote-eval round completed but its payload was discarded because a newer generation
     * superseded it (see the generation guard in [FeaturesViewModel.performNetworkRound]). It is
     * deliberately neither [Success] (nothing was applied) nor [Failed] (nothing went wrong): a
     * caller awaiting the older attributes must NOT be told the refresh succeeded, or it could
     * return a stale evaluation. The awaiter should instead re-join the latest generation (i.e.
     * loop and await again).
     */
    Superseded
}
