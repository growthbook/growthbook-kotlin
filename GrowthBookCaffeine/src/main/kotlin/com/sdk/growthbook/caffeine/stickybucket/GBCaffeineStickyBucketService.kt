package com.sdk.growthbook.caffeine.stickybucket

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Ticker
import com.sdk.growthbook.caffeine.GBCaffeineCacheStats
import com.sdk.growthbook.caffeine.caffeineBuilder
import com.sdk.growthbook.caffeine.requirePositive
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/**
 * [GBStickyBucketService] backed by Caffeine: bounded, in-process sticky bucket assignments.
 *
 * The default service persists one file per user through the caching layer, on the evaluation
 * path. That is fine for an app with a single user and unworkable for a server: it grows a file
 * per identifier and puts disk I/O between a request and its variation. This service keeps
 * assignments in memory instead, under a bound you choose, with optional expiry for identifiers
 * that never come back.
 *
 * ```
 * GBSDKBuilder(...)
 *     .setStickyBucketService(GBCaffeineStickyBucketService(applicationScope))
 *     .initialize()
 * ```
 *
 * ### What in-memory costs you
 *
 * Assignments live and die with the process and are not shared between instances. A user whose
 * assignment was evicted, or who lands on another instance or on a restarted one, is bucketed
 * again from scratch. Bucketing is deterministic, so they normally land in the same variation —
 * the exception is when the experiment's weights, coverage or variations changed in between, which
 * is exactly the case sticky bucketing exists to protect against.
 *
 * So: use this where assignments only need to hold within a process (a single instance, a
 * load balancer that pins a user to one node, tests, or a bound on the memory a fleet spends on
 * assignments). Use a durable, shared store — the Redis service in `:GrowthBookRedis` — when they
 * must survive a restart or be seen by every instance.
 *
 * ### One service per SDK instance
 *
 * Assignments are stored under `attributeName||attributeValue`, which says nothing about *which*
 * SDK produced them. Everything the SDK stores is namespaced by its API key — the feature cache
 * because `GrowthBookSDK` builds that key itself, sticky documents because the default service is
 * given a `gbStickyBuckets__<apiKey>_` prefix when the builder constructs it — and this service
 * follows the same rule, which is what [clientKey] is for.
 *
 * Pass one service to one `GBSDKBuilder`. Handing the same instance to two SDKs (two environments,
 * two projects) puts both their documents in one cache under one user's key, and one SDK's
 * assignments are then served to the other's experiments. The stored keys carry [clientKey] so
 * that mismatch is visible rather than silent, but nothing here can enforce it — build a second
 * service instead.
 *
 * @param coroutineScope scope the SDK uses to persist assignments off the evaluation path; the
 *   interface requires it even though writes here are a map put
 * @param clientKey the SDK API key whose assignments this service holds — the same string passed
 *   to `GBSDKBuilder`. It namespaces the stored keys exactly as the SDK's own default service does.
 * @param maximumSize how many users' assignments to keep before evicting the least recently used.
 *   Bound this to what you are willing to spend — see [DEFAULT_MAXIMUM_SIZE].
 * @param expireAfterWrite evicts a user's assignments this long after they were last written,
 *   whether or not they are being read; `null` (the default) never expires
 * @param expireAfterAccess evicts a user's assignments once they have not been read *or* written
 *   for this long; `null` (the default) never expires. Usually the one you want: it keeps active
 *   users sticky and lets one-off identifiers fall out.
 * @param recordStats whether to count hits, misses and evictions for [stats]; off by default
 *   because recording costs a little on every read and write
 * @param ticker time source for expiry; `null` (the default) uses the system clock. Pass one to
 *   drive expiry deterministically in tests instead of sleeping.
 */
class GBCaffeineStickyBucketService(
    override val coroutineScope: CoroutineScope,
    clientKey: String,
    maximumSize: Long = DEFAULT_MAXIMUM_SIZE,
    expireAfterWrite: Duration? = null,
    expireAfterAccess: Duration? = null,
    private val recordStats: Boolean = false,
    ticker: Ticker? = null
) : GBStickyBucketService {

    /**
     * Documents are held as they are, not serialised: nothing leaves the process, so a JSON
     * round trip per read and write would buy nothing. [GBStickyAssignmentsDocument] is immutable,
     * so a stored document cannot be changed underneath a reader.
     */
    private val cache: Cache<String, GBStickyAssignmentsDocument> =
        caffeineBuilder(expireAfterWrite, expireAfterAccess, recordStats, ticker)
            .maximumSize(maximumSize.requirePositive("maximumSize"))
            .build()

    /**
     * Namespace for the stored keys, in the shape the SDK's own default service uses
     * (`GBSDKBuilder` builds it as `gbStickyBuckets__<apiKey>_`).
     */
    private val keyPrefix: String = "$STICKY_KEY_PREFIX${clientKey}_"

    /**
     * @return the stored document, or `null` when this user has none, theirs expired or was
     *   evicted — a miss simply rebuckets them
     */
    override suspend fun getAssignments(
        attributeName: String,
        attributeValue: String
    ): GBStickyAssignmentsDocument? =
        cache.getIfPresent(cacheKey(docKey(attributeName, attributeValue)))

    /**
     * Writes [doc] whole under its user's key, replacing what was there.
     *
     * The SDK builds the document by merging the new assignment into the snapshot it last read, so
     * two evaluations racing for the same user can drop one of the two assignments. The loser is
     * rebucketed on the next evaluation — the same trade-off the default service makes, and
     * harmless while the experiment's weights have not changed.
     */
    override suspend fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        cache.put(cacheKey(docKey(doc.attributeName, doc.attributeValue)), doc)
    }

    /**
     * Looks every attribute up in one pass rather than one call per attribute.
     *
     * @param attributes attribute name to value, as the SDK holds them
     * @return the documents that were found, keyed `name||value` — the SDK indexes them by that,
     *   not by the namespaced key they are stored under; missing users are left out
     */
    override suspend fun getAllAssignments(
        attributes: Map<String, String>
    ): Map<String, GBStickyAssignmentsDocument> {
        if (attributes.isEmpty()) return emptyMap()

        val documents = mutableMapOf<String, GBStickyAssignmentsDocument>()
        attributes.forEach { (name, value) ->
            val docKey = docKey(name, value)
            cache.getIfPresent(cacheKey(docKey))?.let { documents[docKey] = it }
        }
        return documents
    }

    /**
     * Drops every stored assignment.
     *
     * Note this alone does not rebucket anyone: the SDK evaluates against the snapshot of
     * assignment documents held in its context, which is only replaced when it reloads them. In a
     * user-switch or sign-out flow, call this and then `setAttributes` / `setAttributesSync` —
     * changing attributes is what drops that snapshot and makes the SDK read again.
     */
    fun clear() {
        cache.invalidateAll()
    }

    /**
     * Runs any pending maintenance, applying size-based eviction now.
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
     * The key the SDK indexes its sticky documents by — what [getAllAssignments] must return, and
     * deliberately not what they are stored under.
     */
    private fun docKey(attributeName: String, attributeValue: String): String =
        "$attributeName$KEY_SEPARATOR$attributeValue"

    /** Namespaces a [docKey] for storage. */
    private fun cacheKey(docKey: String): String = "$keyPrefix$docKey"

    companion object {

        /** Separator between attribute name and value; matches the SDK's internal key format. */
        const val KEY_SEPARATOR: String = "||"

        /** Prefix the SDK's own default sticky bucket service uses, ahead of the client key. */
        private const val STICKY_KEY_PREFIX = "gbStickyBuckets__"

        /**
         * Ten thousand users' assignments. A document is a handful of short strings, so this is a
         * small amount of memory and still a real bound — unlike the feature cache, this key space
         * grows with traffic.
         */
        const val DEFAULT_MAXIMUM_SIZE: Long = 10_000L
    }
}
