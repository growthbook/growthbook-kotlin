package com.sdk.growthbook.features

import com.sdk.growthbook.utils.GBError

/** Result of a feature fetch, flowing into [FeaturesViewModel.dispatch]. */
internal sealed interface FetchOutcome {
    /**
     * Usable payload.
     * @param authoritative true when this result unblocks suspendFeature()/initialize{}
     *   (network, or cache still fresh within the TTL); maps to delegate's isRemote.
     */
    data class Ready(val payload: DecodedPayload, val source: Source, val authoritative: Boolean) : FetchOutcome
    data class Failed(val error: GBError, val source: Source) : FetchOutcome
    data object NotModified : FetchOutcome
}
internal enum class Source { CACHE, NETWORK }

internal sealed interface CacheOutcome {
    object ServedFresh: CacheOutcome
    object ServedStaleOrMiss: CacheOutcome
    data class Expired(val stale: DecodedPayload): CacheOutcome
}
