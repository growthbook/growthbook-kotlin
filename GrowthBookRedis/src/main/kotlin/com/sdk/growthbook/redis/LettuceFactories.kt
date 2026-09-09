package com.sdk.growthbook.redis

import com.sdk.growthbook.redis.cache.GBRedisCachingLayer
import com.sdk.growthbook.redis.stickybucket.GBRedisStickyBucketService
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/*
 * Lettuce factories. Kept apart from the Jedis ones for the reason described in JedisFactories.kt:
 * no class should carry both clients' types in its signatures.
 */

/**
 * Builds a sticky bucket service over the Lettuce client.
 *
 * ```
 * GBSDKBuilder(...)
 *     .setStickyBucketService(GBRedisStickyBucketService.lettuce(connection, applicationScope))
 *     .initialize()
 * ```
 *
 * Requires `io.lettuce:lettuce-core` on the runtime classpath — this module only compiles
 * against it.
 *
 * @param connection the Lettuce connection; owned by the caller
 * @param coroutineScope scope the SDK uses to persist assignments off the evaluation path
 * @param keyPrefix namespace applied to every Redis key
 * @param ttl how long an assignment survives; `null` by default, see
 *   [GBRedisStickyBucketService.DEFAULT_TTL]
 * @param onError invoked with any Redis or parsing failure; assignments degrade to a miss
 * @see GBLettuceRedisCommands
 */
fun GBRedisStickyBucketService.Companion.lettuce(
    connection: StatefulRedisConnection<String, String>,
    coroutineScope: CoroutineScope,
    keyPrefix: String = GBRedisStickyBucketService.DEFAULT_KEY_PREFIX,
    ttl: Duration? = GBRedisStickyBucketService.DEFAULT_TTL,
    onError: ((Throwable) -> Unit)? = null
): GBRedisStickyBucketService =
    GBRedisStickyBucketService(
        commands = GBLettuceRedisCommands(connection),
        coroutineScope = coroutineScope,
        keyPrefix = keyPrefix,
        ttl = ttl,
        onError = onError
    )

/**
 * Builds a feature cache layer over the Lettuce client.
 *
 * ```
 * val cache = GBRedisCachingLayer.lettuce(connection, applicationScope, clientKey = <API_KEY>)
 * cache.warmUp()
 * GBSDKBuilder(...).setCachingLayer(cache).initialize()
 * ```
 *
 * Requires `io.lettuce:lettuce-core` on the runtime classpath — this module only compiles
 * against it.
 *
 * @param connection the Lettuce connection; owned by the caller
 * @param coroutineScope scope used to push writes to Redis off the caller's thread
 * @param clientKey the SDK API key whose feature cache this layer backs
 * @param keyPrefix namespace applied to the Redis key
 * @param ttl how long the cached payload survives; `null` by default, see
 *   [GBRedisCachingLayer.DEFAULT_TTL]
 * @param onError invoked with any Redis failure; reads still degrade to a miss
 * @see GBLettuceRedisCommands
 */
fun GBRedisCachingLayer.Companion.lettuce(
    connection: StatefulRedisConnection<String, String>,
    coroutineScope: CoroutineScope,
    clientKey: String,
    keyPrefix: String = GBRedisCachingLayer.DEFAULT_KEY_PREFIX,
    ttl: Duration? = GBRedisCachingLayer.DEFAULT_TTL,
    onError: ((Throwable) -> Unit)? = null
): GBRedisCachingLayer =
    GBRedisCachingLayer(
        commands = GBLettuceRedisCommands(connection),
        coroutineScope = coroutineScope,
        clientKey = clientKey,
        keyPrefix = keyPrefix,
        ttl = ttl,
        onError = onError
    )
