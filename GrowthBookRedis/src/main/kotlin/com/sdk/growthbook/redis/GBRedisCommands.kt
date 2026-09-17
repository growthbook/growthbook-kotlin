package com.sdk.growthbook.redis

/**
 * The Redis operations this module needs, decoupled from any particular client.
 *
 * Implement it to plug in a client we do not ship an adapter for — `UnifiedJedis`, a cluster
 * client, Spring's `RedisTemplate`, or a multiplatform client such as `eu.vendeli:rethis`.
 *
 * Implementations own no lifecycle: the caller creates and closes the underlying client.
 * A missing key is a normal result, not a failure — return `null` for it rather than throwing.
 */
interface GBRedisCommands {

    /** @return the value stored under [key], or `null` when the key is absent. */
    suspend fun get(key: String): String?

    /**
     * Stores [value] under [key].
     *
     * @param ttlSeconds expiry in seconds, or `null` for an entry that never expires. It is at
     *   least `1` when set — callers validate that, so an implementation never has to guard
     *   against `EX 0`, which Redis rejects.
     */
    suspend fun set(key: String, value: String, ttlSeconds: Long?)

    /**
     * Fetches [keys] in a single round trip.
     *
     * @return values **positionally aligned** with [keys]; `null` for each absent key
     */
    suspend fun mget(keys: List<String>): List<String?>
}
