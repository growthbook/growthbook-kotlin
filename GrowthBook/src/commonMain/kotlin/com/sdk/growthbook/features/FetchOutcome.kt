package com.sdk.growthbook.features

import com.sdk.growthbook.utils.GBError

/** Result of a feature fetch, flowing into [FeaturesViewModel.dispatch]. */
internal sealed interface FetchOutcome {
    /**
     * Usable payload.
     * @param authoritative true when this result unblocks suspendFeature()/initialize{}
     *   (network, or cache still fresh within the TTL); maps to delegate's isRemote.
     * @param staleError set only on the stale-if-error fallback: the payload is an expired cache
     *   served because the revalidating network round failed with this error. Non-null is what
     *   tells the delegate this Ready is a fallback rather than an ordinary cache read.
     */
    data class Ready(
        val payload: DecodedPayload,
        val source: Source,
        val authoritative: Boolean,
        val staleError: GBError? = null,
    ) : FetchOutcome
    /**
     * Nothing usable came back: a transport error, a timeout, or a payload that decoded to neither
     * features nor saved groups. [source] separates a failed network round from a failed cache
     * read — the latter is not a refresh result, so it leaves the refresh handler silent.
     */
    data class Failed(val error: GBError, val source: Source) : FetchOutcome

    /**
     * The server answered 304: what is cached is current, nothing to apply. Its own outcome rather
     * than a [Ready] with an unchanged payload, because there is no payload to carry.
     */
    data object NotModified : FetchOutcome
}

/** Where a [FetchOutcome] came from. Note a cache read can fail too, hence [FetchOutcome.Failed]'s source. */
internal enum class Source { CACHE, NETWORK }

/**
 * What [FeaturesViewModel.serveCache] did, and therefore whether the caller still needs the
 * network. Distinct from [FetchOutcome]: serveCache already dispatched the outcome (if any) to the
 * delegate, so this reports only the consequence for the fetch that follows.
 */
internal sealed interface CacheOutcome {
    /**
     * A usable payload was served as authoritative and the network round can be skipped entirely.
     * Requires all three: a plain cache-gated fetch, a [CacheZone.FRESH] entry, and a payload that
     * actually decoded.
     */
    object ServedFresh: CacheOutcome

    /**
     * Go to the network. Deliberately lumps together every case that leaves us without an
     * authoritative cache answer: nothing cached, an unreadable or undecodable entry, a
     * [CacheZone.STALE] one already dispatched as non-authoritative, and remote-eval mode, which
     * bypasses the cache outright. They differ in cause but not in what happens next.
     */
    object ServedStaleOrMiss: CacheOutcome

    /**
     * The entry is past the hard freshness ceiling, so it was **not** served — evaluation must not
     * see data that old while the network is reachable. [stale] carries it along purely as the
     * `stale-if-error` fallback, used only if the revalidating round fails and
     * [CachePolicy.serveStaleOnError] is on.
     */
    data class Expired(val stale: DecodedPayload): CacheOutcome
}
