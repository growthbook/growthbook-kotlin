package com.sdk.growthbook.features

/**
 * Controls how [FeaturesViewModel.fetchFeatures] balances cache and network.
 */
internal enum class FetchPolicy {
    /** Serve fresh cache (within the configured max age) and skip the network when possible. */
    CacheFirst,

    /** Ignore cache freshness and always hit the network. */
    ForceNetwork
}
