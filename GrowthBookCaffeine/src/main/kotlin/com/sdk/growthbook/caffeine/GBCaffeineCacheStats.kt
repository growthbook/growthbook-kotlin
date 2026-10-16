package com.sdk.growthbook.caffeine

/**
 * Immutable snapshot of a Caffeine cache's counters.
 *
 * Its own type rather than Caffeine's `CacheStats`, so the shape of what this module reports does
 * not change underneath consumers when the Caffeine version moves.
 *
 * Recording statistics costs a little on every read and write, so it is off by default. When it is
 * off every counter reads `0` — check [isRecording] before drawing conclusions from a snapshot of
 * zeroes.
 *
 * @param isRecording whether the cache was configured to record statistics at all
 * @param hitCount lookups that were served from the cache
 * @param missCount lookups that found no entry
 * @param evictionCount entries removed by size, weight or expiry — not by an explicit clear
 */
data class GBCaffeineCacheStats(
    val isRecording: Boolean,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long
)
