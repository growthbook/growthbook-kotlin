package com.sdk.growthbook.redis

import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await

/**
 * [GBRedisCommands] backed by the Lettuce client.
 *
 * Commands are issued asynchronously and awaited without blocking a thread, so unlike the Jedis
 * adapter this one needs no dispatcher of its own.
 *
 * The connection's lifecycle stays with the caller; a single Lettuce connection is thread-safe and
 * meant to be shared.
 *
 * ```
 * val client = RedisClient.create("redis://localhost:6379")
 * val commands = GBLettuceRedisCommands(client.connect())
 * ```
 *
 * @param connection the Lettuce connection used to reach Redis; owned by the caller
 */
class GBLettuceRedisCommands(
    connection: StatefulRedisConnection<String, String>
) : GBRedisCommands {

    private val commands = connection.async()

    override suspend fun get(key: String): String? = commands.get(key).await()

    override suspend fun set(key: String, value: String, ttlSeconds: Long?) {
        // SET ... EX rather than SET followed by EXPIRE: one round trip, and no window in which
        // the key exists without its expiry. SETEX is deprecated in Lettuce 7.
        if (ttlSeconds == null) {
            commands.set(key, value).await()
        } else {
            commands.set(key, value, SetArgs.Builder.ex(ttlSeconds)).await()
        }
    }

    override suspend fun mget(keys: List<String>): List<String?> =
        commands.mget(*keys.toTypedArray()).await()
            // Lettuce reports an absent key as a KeyValue without a value, not as a null entry.
            .map { keyValue -> if (keyValue.hasValue()) keyValue.value else null }
}
