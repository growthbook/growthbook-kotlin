package com.sdk.growthbook.redis.cache

import com.sdk.growthbook.redis.GBRedisCommands
import com.sdk.growthbook.redis.failOpen
import com.sdk.growthbook.redis.report
import com.sdk.growthbook.redis.toTtlSeconds
import com.sdk.growthbook.sandbox.GBCachingLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * [GBCachingLayer] backed by Redis, so a horizontally scaled fleet shares one feature cache and a
 * cold instance can serve features before its first network fetch returns.
 *
 * [GBCachingLayer] is deliberately synchronous — the SDK reads the cache inside the non-suspending
 * `initialize()` — while Redis is not. Rather than block that call, this layer answers reads from
 * memory and talks to Redis around them:
 *
 * - [getContent] returns whatever is in memory and never touches Redis;
 * - [saveContent] updates memory immediately and signals a single writer coroutine, which runs on
 *   [coroutineScope] for as long as that scope lives and pushes the *current* payload. Pushes are
 *   therefore serialised, and rapid writes collapse into one — a superseded payload can never be
 *   the one Redis keeps;
 * - [warmUp] is the only read of Redis, and the caller awaits it *before* `initialize()`.
 *
 * Skipping [warmUp] is allowed and simply means the first start misses the cache and falls back to
 * the network — the cache is an optimisation, not a source of truth. The in-memory copy also does
 * not observe writes made by other instances until it is warmed again.
 *
 * ```
 * val cache = GBRedisCachingLayer(commands, applicationScope, clientKey = <API_KEY>)
 * cache.warmUp()                               // suspend; do this first
 * GBSDKBuilder(...).setCachingLayer(cache).initialize()
 * ```
 *
 * ### Scope: the feature cache only
 *
 * This layer serves exactly one key — the feature cache of [clientKey] — and reports anything else
 * through [onError] as a [GBRedisCacheScopeException] without touching Redis. That matters because
 * `GBSDKBuilder.setCachingLayer` also routes the **default** sticky bucket service through the
 * caching layer, and this layer cannot serve it: sticky documents are keyed per user and read
 * synchronously, so they could never be warmed up ahead of time. Left unchecked they would be
 * written to Redis and never read back, silently rebucketing every user on restart.
 *
 * For sticky bucketing pass the dedicated service instead — the two are designed to be used
 * together, and an explicit service takes precedence over the caching layer:
 *
 * ```
 * GBSDKBuilder(...)
 *     .setStickyBucketService(GBRedisStickyBucketService.jedis(jedis, applicationScope))
 *     .setCachingLayer(cache)
 *     .initialize()
 * ```
 *
 * Redis failures degrade to a cache miss and are reported to [onError] rather than thrown, so a
 * Redis outage cannot fail SDK initialisation.
 *
 *
 * @param commands the Redis client adapter; the client's lifecycle stays with the caller
 * @param coroutineScope scope the writer coroutine runs on, pushing writes to Redis off the
 *   caller's thread; it lives until this scope is cancelled
 * @param clientKey the SDK API key whose feature cache this layer backs; one layer per SDK instance
 * @param keyPrefix namespace applied to the Redis key
 * @param ttl how long the cached payload survives in Redis; `null` (the default) means it never
 *   expires — see [DEFAULT_TTL]. Must be at least one second when set.
 * @param onError invoked with any Redis failure; reads still degrade to a miss
 */
class GBRedisCachingLayer(
    private val commands: GBRedisCommands,
    private val coroutineScope: CoroutineScope,
    clientKey: String,
    keyPrefix: String = DEFAULT_KEY_PREFIX,
    ttl: Duration? = DEFAULT_TTL,
    private val onError: ((Throwable) -> Unit)? = null
) : GBCachingLayer {

    /** Validated here rather than per write; see `Duration?.toTtlSeconds`. */
    private val ttlSeconds: Long? = ttl.toTtlSeconds()

    /**
     * Mirrors the name the SDK asks its caching layer for — `Constants.FEATURE_CACHE` plus the
     * client key, built in `GrowthBookSDK`. Duplicated because that constant is internal.
     */
    private val featureCacheName: String = "$FEATURE_CACHE_PREFIX$clientKey"

    /** The one Redis key this layer touches. */
    private val redisKey: String = "$keyPrefix$featureCacheName"

    /**
     * A single slot rather than a map: the layer serves one key, so it cannot accumulate an
     * entry per user the way an unscoped store would.
     */
    @Volatile
    private var payload: String? = null

    /**
     * Guards [onError] against a flood: a misconfiguration reports once, not on every evaluation
     * of every user.
     */
    private val scopeReported = AtomicBoolean(false)

    /**
     * Signals the writer that [payload] changed. Conflated, because the writer reads the current
     * payload rather than a queued one: while a push is in flight, any number of further writes
     * collapse into a single follow-up.
     */
    private val pendingWrite = Channel<Unit>(Channel.CONFLATED)

    init {
        // One writer, so pushes are serialised. A plain `launch` per write would put concurrent
        // round trips on the wire, and Redis would keep whichever landed last — which is not
        // necessarily the newest payload. Writing `payload` rather than the value captured at
        // call time also means a superseded payload is never pushed at all.
        coroutineScope.launch {
            for (signal in pendingWrite) {
                val current = payload ?: continue
                failOpen(Unit) { commands.set(redisKey, current, ttlSeconds) }
            }
        }
    }

    /**
     * Answers from memory, never from Redis — the SDK calls this on the non-suspending
     * `initialize()` path.
     *
     * @param fileName the cache name the SDK asks for; anything but this layer's own is a miss
     * @return the payload put there by [warmUp] or [saveContent], or `null` when neither has run
     */
    override fun getContent(fileName: String): String? =
        if (inScope(fileName)) payload else null

    /**
     * Records [content] as the current payload and wakes the writer, which pushes it to Redis.
     *
     * Returns immediately: the round trip happens on `coroutineScope`, so a slow or unreachable
     * Redis never delays the SDK, and a subsequent [getContent] already sees the new payload.
     *
     * @param fileName the cache name the SDK writes under; anything but this layer's own is
     *   dropped and reported to `onError`
     */
    override fun saveContent(fileName: String, content: String) {
        if (!inScope(fileName)) return

        payload = content
        pendingWrite.trySend(Unit)
    }

    /**
     * Loads the feature cache for this layer's client key into memory.
     *
     * Await this before `initialize()`; it is a single Redis round trip. A miss, or a Redis
     * failure, simply leaves the cache empty so the SDK falls back to the network.
     */
    suspend fun warmUp() {
        failOpen(null) { commands.get(redisKey) }
            ?.let { loaded -> payload = loaded }
    }

    /**
     * Whether [fileName] is the one key this layer backs, reporting the first key that is not.
     */
    private fun inScope(fileName: String): Boolean {
        if (fileName == featureCacheName) return true
        if (scopeReported.compareAndSet(false, true)) {
            report(onError, GBRedisCacheScopeException(OUT_OF_SCOPE_MESSAGE))
        }
        return false
    }

    /** Binds this layer's `onError` to the shared helper, so call sites stay readable. */
    private suspend fun <T> failOpen(fallback: T, block: suspend () -> T): T =
        failOpen(fallback, onError, block)

    companion object {

        /**
         * Empty by default: the cache key already carries the client key, and unlike the sticky
         * bucket documents this payload is not shared with the other SDKs.
         */
        const val DEFAULT_KEY_PREFIX: String = ""

        /**
         * No expiry by default. The cache holds one key per client key, so it does not grow, and
         * the payload carries its own age (`cachedAt`) which `setCacheMaxAge` already gates on —
         * a Redis TTL would only decide how long a *cold* instance may still warm up from it.
         * Set one to bound how stale a restarted instance's first answer can be.
         */
        val DEFAULT_TTL: Duration? = null

        private const val FEATURE_CACHE_PREFIX = "FeatureCache_"

        // Says nothing about the key itself: a sticky bucket key embeds the attribute value
        // identifying the user.
        private const val OUT_OF_SCOPE_MESSAGE =
            "GBRedisCachingLayer backs the feature cache of its client key and was asked for " +
                "another key. It is most likely wired up as the store behind the default sticky " +
                "bucket service, which it cannot serve: reads are answered from memory, so those " +
                "assignments would be written to Redis and never read back. Pass " +
                "GBRedisStickyBucketService to GBSDKBuilder.setStickyBucketService(...) instead."
    }
}
