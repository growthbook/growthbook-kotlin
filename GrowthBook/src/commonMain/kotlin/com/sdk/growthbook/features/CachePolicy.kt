package com.sdk.growthbook.features

/** Freshness classification of a cached payload relative to a [CachePolicy]. */
internal enum class CacheZone {
    /** Younger than the inner window: serve from cache, skip the network. */
    FRESH,

    /** Past the inner window but still usable: serve immediately while revalidating over the network. */
    STALE,

    /** Older than the hard ceiling: do not serve; refetch (offline fallback governed by [CachePolicy.serveStaleOnError]). */
    EXPIRED,
}

/**
 * Cohesive cache-freshness policy: turns the three tuning knobs into a single [classify] decision so
 * [FeaturesViewModel.serveCache] no longer juggles nullable thresholds and conditional cutoffs.
 *
 * Zones (only meaningful for a cache-gated fetch):
 *   - age < staleTtl (?: cacheMaxAge)  -> [CacheZone.FRESH]
 *   - staleTtl <= age < cacheMaxAge    -> [CacheZone.STALE]
 *   - age >= cacheMaxAge               -> [CacheZone.EXPIRED]
 *
 * The hard ceiling ([CacheZone.EXPIRED]) is armed ONLY when [staleTtl] is set; [cacheMaxAge] used
 * alone keeps its 7.3.0 behaviour (inner window + serve-stale beyond, no cutoff), so existing
 * consumers are unaffected.
 *
 * @property serveStaleOnError when true, an [CacheZone.EXPIRED] cache is served as a last resort if
 *   the revalidating network round fails (HTTP `stale-if-error` semantics); default fails closed.
 */
internal class CachePolicy(
    private val staleTtl: Long?,
    private val cacheMaxAge: Long?,
    val serveStaleOnError: Boolean = false,
) {
    init {
        if (staleTtl != null && cacheMaxAge != null) {
            require(staleTtl < cacheMaxAge) {
                "staleTtl ($staleTtl) must be smaller than cacheMaxAge ($cacheMaxAge)"
            }
        }
    }

    /** Classifies a cache entry aged [ageMs] milliseconds. */
    fun classify(ageMs: Long): CacheZone {
        if (staleTtl != null && cacheMaxAge != null && ageMs >= cacheMaxAge) return CacheZone.EXPIRED
        val innerWindow = staleTtl ?: cacheMaxAge
        return if (innerWindow != null && ageMs < innerWindow) CacheZone.FRESH else CacheZone.STALE
    }
}
