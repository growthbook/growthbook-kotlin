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
    NotModified
}
