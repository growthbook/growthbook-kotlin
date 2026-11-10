package com.sdk.growthbook.caffeine.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Ticker
import com.github.benmanes.caffeine.cache.Weigher
import com.sdk.growthbook.caffeine.GBCaffeineCacheStats
import com.sdk.growthbook.caffeine.caffeineBuilder
import com.sdk.growthbook.caffeine.requirePositive
import com.sdk.growthbook.sandbox.GBCachingLayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration

/**
 * [GBCachingLayer] backed by Caffeine: a bounded, in-process feature cache with optional expiry.
 *
 * Unlike the per-target default, this layer writes nothing to disk. That is the point of it on a
 * server: no file handles on the evaluation path, no cache files to clean up in a container, and a
 * bound on how much memory cached payloads may take. The trade-off is that a restarted process
 * starts cold and fetches from the network — the cache is an optimisation, not a source of truth.
 * Reach for a shared, durable store instead when a cold instance should be able to serve features
 * before its first fetch returns.
 *
 * ```
 * GBSDKBuilder(...)
 *     .setCachingLayer(GBCaffeineCachingLayer())
 *     .initialize()
 * ```
 *
 * ### Expiry is eviction, not freshness
 *
 * [expireAfterWrite] and [expireAfterAccess] only decide how long a payload is *kept*. They are not
 * the SDK's staleness policy: how old a cached payload may be before it is refreshed or refused is
 * decided by the payload's own timestamp through `setCacheMaxAge` / `setStaleTtl`, and this layer
 * cannot influence that. An evicted entry is simply a cache miss, and the SDK goes to the network.
 *
 * ### Scope: feature caches only
 *
 * This layer serves the SDK's feature cache keys — one per client key, so a single layer can back
 * several SDK instances — and reports anything else through [onError] as a
 * [GBCaffeineCacheScopeException] without storing it. That matters because
 * `GBSDKBuilder.setCachingLayer` also routes the **default** sticky bucket service through the
 * caching layer. Sticky documents are keyed per user, so they would share this cache's bound with
 * the feature payload and be evicted by it — silently rebucketing users, which is the one thing
 * sticky bucketing exists to prevent.
 *
 * For sticky bucketing pass the dedicated service instead — the two are designed to be used
 * together, and an explicit service takes precedence over the caching layer:
 *
 * ```
 * GBSDKBuilder(...)
 *     .setStickyBucketService(GBCaffeineStickyBucketService(applicationScope))
 *     .setCachingLayer(GBCaffeineCachingLayer())
 *     .initialize()
 * ```
 *
 * @param maximumSize how many payloads to keep before evicting the least recently used one.
 *   Ignored when [maximumWeightBytes] is set. The default is effectively no bound for the usual
 *   process holding one or a handful of client keys — see [DEFAULT_MAXIMUM_SIZE].
 * @param maximumWeightBytes bounds the cache by the total UTF-8 size of the cached payloads
 *   instead of by entry count; `null` (the default) bounds by count. The better bound when memory
 *   is what you are protecting, since one payload can be orders of magnitude larger than another.
 *   Keep it comfortably above the largest payload you expect: Caffeine admits an entry heavier
 *   than the whole bound and then evicts it on the next maintenance pass, so that client key would
 *   never hit the cache and every cold start would go to the network.
 * @param expireAfterWrite evicts a payload this long after it was cached, whether or not it is
 *   being read; `null` (the default) never expires. See the note above — this is not freshness.
 * @param expireAfterAccess evicts a payload that has not been read *or* written for this long;
 *   `null` (the default) never expires. Every SDK read counts as an access and resets the timer.
 * @param recordStats whether to count hits, misses and evictions for [stats]; off by default
 *   because recording costs a little on every read and write
 * @param ticker time source for expiry; `null` (the default) uses the system clock. Pass one to
 *   drive expiry deterministically in tests instead of sleeping.
 * @param onError invoked when the layer is asked for a key outside its scope; reads and writes of
 *   such a key are dropped either way. With no callback (the default) the misconfiguration is
 *   logged through `java.util.logging` instead — see [report].
 */
class GBCaffeineCachingLayer(
    maximumSize: Long = DEFAULT_MAXIMUM_SIZE,
    maximumWeightBytes: Long? = null,
    expireAfterWrite: Duration? = null,
    expireAfterAccess: Duration? = null,
    private val recordStats: Boolean = false,
    ticker: Ticker? = null,
    private val onError: ((Throwable) -> Unit)? = null
) : GBCachingLayer {

    private val cache: Cache<String, String> =
        caffeineBuilder(expireAfterWrite, expireAfterAccess, recordStats, ticker).let { builder ->
            // Validated even when the weight bound is what ends up being applied: a bad value
            // should fail where it was configured rather than be silently ignored.
            val boundedSize = maximumSize.requirePositive("maximumSize")

            // Built inside each branch rather than after them: attaching a weigher changes the
            // builder's type parameters, so the two branches have no common type to share.
            if (maximumWeightBytes != null) {
                builder
                    .maximumWeight(maximumWeightBytes.requirePositive("maximumWeightBytes"))
                    .weigher(PAYLOAD_WEIGHER)
                    .build()
            } else {
                builder.maximumSize(boundedSize).build()
            }
        }

    /**
     * Guards [onError] against a flood: a misconfiguration reports once, not on every evaluation
     * of every user.
     */
    private val scopeReported = AtomicBoolean(false)

    /**
     * @param fileName the cache name the SDK asks for; anything outside this layer's scope is a
     *   miss
     * @return the cached payload, or `null` when it was never written, has expired or was evicted
     */
    override fun getContent(fileName: String): String? =
        if (inScope(fileName)) cache.getIfPresent(fileName) else null

    /**
     * @param fileName the cache name the SDK writes under; anything outside this layer's scope is
     *   dropped and reported to `onError`
     */
    override fun saveContent(fileName: String, content: String) {
        if (!inScope(fileName)) return

        cache.put(fileName, content)
    }

    /**
     * Drops every cached payload.
     *
     * This does not change what the next evaluation returns: the SDK evaluates against the
     * features held in its own context, not against this cache. What it affects is the next *cold*
     * read — a new SDK instance starting up, or a fetch that would otherwise have been answered
     * from the cache.
     *
     * Not part of [GBCachingLayer] — call it on the layer you built.
     */
    fun clear() {
        cache.invalidateAll()
    }

    /**
     * Runs any pending maintenance, applying size- and weight-based eviction now.
     *
     * Caffeine evicts asynchronously, so this exists for tests that assert on eviction and for
     * reclaiming memory on demand.
     */
    fun cleanUp() {
        cache.cleanUp()
    }

    /**
     * @return a snapshot of the cache's counters; all zeroes unless `recordStats` was enabled
     */
    fun stats(): GBCaffeineCacheStats {
        val snapshot = cache.stats()
        return GBCaffeineCacheStats(
            isRecording = recordStats,
            hitCount = snapshot.hitCount(),
            missCount = snapshot.missCount(),
            evictionCount = snapshot.evictionCount()
        )
    }

    /**
     * Whether [fileName] is a feature cache, reporting the first key that is not.
     */
    private fun inScope(fileName: String): Boolean {
        if (fileName.startsWith(FEATURE_CACHE_PREFIX)) return true
        if (scopeReported.compareAndSet(false, true)) {
            report(GBCaffeineCacheScopeException(OUT_OF_SCOPE_MESSAGE))
        }
        return false
    }

    /**
     * Hands [error] to [onError] without letting the callback break anything: it is consumer code
     * called from the SDK's evaluation path, where a throw would surface as a failed evaluation.
     *
     * With no callback the failure is logged rather than dropped. The misconfiguration this guard
     * exists to catch is invisible by construction — features keep working while sticky bucketing
     * quietly does nothing — so going silent in the default configuration would defeat the guard.
     * `java.util.logging` rather than the SDK's own logger, which is `internal` to `:GrowthBook`.
     * The message carries neither the key nor any attribute value.
     */
    private fun report(error: Throwable) {
        val callback = onError ?: run {
            LOGGER.log(Level.WARNING, error.message, error)
            return
        }

        try {
            callback(error)
        } catch (_: Throwable) {
            // Deliberately swallowed: there is nowhere left to report a failing error callback to.
        }
    }

    companion object {

        /**
         * Effectively no bound: the SDK asks for one key per client key, and a process normally
         * holds one. It is a backstop against unbounded growth rather than a tuning knob — reach
         * for `maximumWeightBytes` when memory is the concern.
         */
        const val DEFAULT_MAXIMUM_SIZE: Long = 1000L

        /** Where a scope violation goes when the consumer gave no `onError` — see [report]. */
        private val LOGGER: Logger = Logger.getLogger(GBCaffeineCachingLayer::class.java.name)

        /**
         * Mirrors the prefix the SDK asks its caching layer for — `Constants.FEATURE_CACHE` plus
         * `_`, built in `GrowthBookSDK`. Duplicated because that constant is internal.
         */
        private const val FEATURE_CACHE_PREFIX = "FeatureCache_"

        /**
         * Weighs a payload by its UTF-8 size. The key is ignored: it is a fixed-length prefix plus
         * a client key, negligible next to a feature payload.
         */
        private val PAYLOAD_WEIGHER = Weigher<String, String> { _, payload ->
            payload.toByteArray(Charsets.UTF_8).size
        }

        // Says nothing about the key itself: a sticky bucket key embeds the attribute value
        // identifying the user.
        private const val OUT_OF_SCOPE_MESSAGE =
            "GBCaffeineCachingLayer backs the SDK's feature cache and was asked for another key. " +
                "It is most likely wired up as the store behind the default sticky bucket " +
                "service, which it must not serve: per-user assignments would share this cache's " +
                "size bound with the feature payload and be evicted by it, rebucketing users. " +
                "Pass GBCaffeineStickyBucketService to " +
                "GBSDKBuilder.setStickyBucketService(...) instead."
    }
}
