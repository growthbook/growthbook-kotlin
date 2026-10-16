package com.sdk.growthbook.redis

import com.sdk.growthbook.redis.cache.GBRedisCachingLayer
import com.sdk.growthbook.redis.stickybucket.GBRedisStickyBucketService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import redis.clients.jedis.UnifiedJedis
import kotlin.time.Duration

/*
 * Jedis factories, kept in their own file so no class carries both `UnifiedJedis` and Lettuce's
 * `StatefulRedisConnection` in its signatures. Both clients are `compileOnly`, and while HotSpot
 * resolves lazily — a consumer with only one of them on the classpath links fine either way —
 * eager analysers such as GraalVM native-image do not. One file per client removes the question.
 *
 * These are extensions on the companion objects, so the call site stays
 * `GBRedisStickyBucketService.jedis(...)`; only the import moves.
 */

/**
 * Builds a sticky bucket service over the Jedis client.
 *
 * ```
 * GBSDKBuilder(...)
 *     .setStickyBucketService(
 *         GBRedisStickyBucketService.jedis(RedisClient.create("localhost", 6379), applicationScope)
 *     )
 *     .initialize()
 * ```
 *
 * Requires `redis.clients:jedis` on the runtime classpath — this module only compiles against it.
 *
 * @param jedis the Jedis client; owned by the caller
 * @param coroutineScope scope the SDK uses to persist assignments off the evaluation path
 * @param keyPrefix namespace applied to every Redis key
 * @param ttl how long an assignment survives; `null` by default, see
 *   [GBRedisStickyBucketService.DEFAULT_TTL]
 * @param dispatcher dispatcher Jedis' blocking calls are moved to
 * @param onError invoked with any Redis or parsing failure; assignments degrade to a miss
 * @see GBJedisRedisCommands
 */
fun GBRedisStickyBucketService.Companion.jedis(
    jedis: UnifiedJedis,
    coroutineScope: CoroutineScope,
    keyPrefix: String = GBRedisStickyBucketService.DEFAULT_KEY_PREFIX,
    ttl: Duration? = GBRedisStickyBucketService.DEFAULT_TTL,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    onError: ((Throwable) -> Unit)? = null
): GBRedisStickyBucketService =
    GBRedisStickyBucketService(
        commands = GBJedisRedisCommands(jedis, dispatcher),
        coroutineScope = coroutineScope,
        keyPrefix = keyPrefix,
        ttl = ttl,
        onError = onError
    )

/**
 * Builds a feature cache layer over the Jedis client.
 *
 * ```
 * val cache = GBRedisCachingLayer.jedis(jedis, applicationScope, clientKey = <API_KEY>)
 * cache.warmUp()
 * GBSDKBuilder(...).setCachingLayer(cache).initialize()
 * ```
 *
 * Requires `redis.clients:jedis` on the runtime classpath — this module only compiles against it.
 *
 * @param jedis the Jedis client; owned by the caller
 * @param coroutineScope scope used to push writes to Redis off the caller's thread
 * @param clientKey the SDK API key whose feature cache this layer backs
 * @param keyPrefix namespace applied to the Redis key
 * @param ttl how long the cached payload survives; `null` by default, see
 *   [GBRedisCachingLayer.DEFAULT_TTL]
 * @param dispatcher dispatcher Jedis' blocking calls are moved to
 * @param onError invoked with any Redis failure; reads still degrade to a miss
 * @see GBJedisRedisCommands
 */
fun GBRedisCachingLayer.Companion.jedis(
    jedis: UnifiedJedis,
    coroutineScope: CoroutineScope,
    clientKey: String,
    keyPrefix: String = GBRedisCachingLayer.DEFAULT_KEY_PREFIX,
    ttl: Duration? = GBRedisCachingLayer.DEFAULT_TTL,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    onError: ((Throwable) -> Unit)? = null
): GBRedisCachingLayer =
    GBRedisCachingLayer(
        commands = GBJedisRedisCommands(jedis, dispatcher),
        coroutineScope = coroutineScope,
        clientKey = clientKey,
        keyPrefix = keyPrefix,
        ttl = ttl,
        onError = onError
    )
