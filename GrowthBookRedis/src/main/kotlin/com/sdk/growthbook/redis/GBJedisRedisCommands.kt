package com.sdk.growthbook.redis

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.params.SetParams

/**
 * [GBRedisCommands] backed by the Jedis client.
 *
 * Jedis is blocking, so every command runs on [dispatcher] — otherwise a Redis round trip would
 * stall whichever thread the SDK evaluates features on.
 *
 * [UnifiedJedis] is the common supertype of every Jedis topology (`RedisClient`,
 * `RedisClusterClient`, `JedisCluster`, and `JedisPooled` on older versions), so one adapter
 * covers them all. It is taken deliberately in place of the deprecated `JedisPool`.
 *
 * The client's lifecycle stays with the caller: it is used but never closed here.
 *
 * ```
 * val commands = GBJedisRedisCommands(RedisClient.create("localhost", 6379))
 * ```
 *
 * Note for Redis Cluster: [mget] issues a single MGET, which Redis rejects with `CROSSSLOT` when
 * the keys live on different slots. Assignments then degrade to a miss rather than failing the
 * evaluation, but stickiness is lost — implement [GBRedisCommands] with a slot-aware fan-out if
 * you need it.
 *
 * @param jedis the Jedis client used to reach Redis; owned by the caller
 * @param dispatcher dispatcher the blocking calls are moved to
 */
class GBJedisRedisCommands(
    private val jedis: UnifiedJedis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : GBRedisCommands {

    override suspend fun get(key: String): String? =
        withContext(dispatcher) { jedis.get(key) }

    override suspend fun set(key: String, value: String, ttlSeconds: Long?) {
        withContext(dispatcher) {
            // SET ... EX rather than SET followed by EXPIRE: one round trip, and no window in
            // which the key exists without its expiry. SETEX is deprecated in Jedis 8.
            if (ttlSeconds == null) {
                jedis.set(key, value)
            } else {
                jedis.set(key, value, SetParams.setParams().ex(ttlSeconds))
            }
        }
    }

    override suspend fun mget(keys: List<String>): List<String?> =
        withContext(dispatcher) { jedis.mget(*keys.toTypedArray()) }
}
