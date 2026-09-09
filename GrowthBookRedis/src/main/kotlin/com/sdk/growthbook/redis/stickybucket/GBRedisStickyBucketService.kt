package com.sdk.growthbook.redis.stickybucket

import com.sdk.growthbook.redis.GBRedisCommands
import com.sdk.growthbook.redis.failOpen
import com.sdk.growthbook.redis.toTtlSeconds
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/**
 * [GBStickyBucketService] backed by Redis, so assignments are shared across a horizontally
 * scaled fleet and survive restarts.
 *
 * Documents are stored as one JSON string per user under
 * `<keyPrefix><attributeName>||<attributeValue>`. With the default (empty) prefix the layout
 * matches the TypeScript SDK's `RedisStickyBucketService`, so the two can share one Redis.
 *
 * Redis failures and malformed payloads degrade to a miss and are reported to [onError] rather
 * than thrown: assignments are read on the evaluation path and written from a fire-and-forget
 * coroutine, where an exception would take the caller's scope down with it.
 *
 * Use `GBRedisStickyBucketService.jedis(...)` or `.lettuce(...)` to build one over a shipped
 * adapter; both live in `com.sdk.growthbook.redis` and must be imported alongside this class.
 *
 * ### Concurrent writes can drop an assignment
 *
 * A document is written whole, and the assignments it contains were merged in memory against the
 * snapshot this instance last read (`GBUtils.generateStickyBucketAssignmentDoc`). Two instances
 * whose snapshots were taken before either wrote will therefore overwrite each other:
 *
 * ```
 * A and B both read id||u42  -> empty
 * A buckets "checkout"       -> SET id||u42 {checkout: variant}
 * B buckets "banner"         -> SET id||u42 {banner: control}     // checkout is gone
 * ```
 *
 * The lost assignment is rebucketed on the next evaluation. Bucketing is deterministic, so the
 * user normally lands in the same variation again — the exception is when the experiment's
 * weights, coverage or variations changed in between, which is exactly the case sticky bucketing
 * exists to protect. The window is between one instance's read and another's write, so it opens
 * only when two instances evaluate different experiments for the same user at nearly the same
 * time; it stays shut when a load balancer pins a user to one instance.
 *
 * This is a property of sharing one store rather than a defect here: the TypeScript Redis service
 * writes the whole document too, and the per-process file cache this replaces could not hit it at
 * all, having no shared document to clobber. It could be closed with an atomic read-merge-write (a
 * Lua script behind an opt-in capability interface, so custom [GBRedisCommands] implementations
 * keep working) — deliberately left for later, since in a fleet shared with another SDK it would
 * only protect this SDK's own writes.
 *
 * @param commands the Redis client adapter; the client's lifecycle stays with the caller
 * @param coroutineScope scope the SDK uses to persist assignments off the evaluation path
 * @param keyPrefix namespace applied to every Redis key
 * @param ttl how long an assignment survives; `null` (the default) means it never expires — see
 *   [DEFAULT_TTL] for the trade-off. Must be at least one second when set.
 * @param onError invoked with any Redis or parsing failure; assignments still degrade to a miss
 */
class GBRedisStickyBucketService(
    private val commands: GBRedisCommands,
    override val coroutineScope: CoroutineScope,
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
    ttl: Duration? = DEFAULT_TTL,
    private val onError: ((Throwable) -> Unit)? = null
) : GBStickyBucketService {

    /** Validated here rather than per write; see `Duration?.toTtlSeconds`. */
    private val ttlSeconds: Long? = ttl.toTtlSeconds()

    /**
     * Reads the document for one user in a single `GET`.
     *
     * @return the stored document, or `null` when the key is absent, the payload cannot be parsed
     *   or Redis is unreachable — a miss simply rebuckets the user
     */
    override suspend fun getAssignments(
        attributeName: String,
        attributeValue: String
    ): GBStickyAssignmentsDocument? =
        failOpen(null) {
            commands.get(redisKey(docKey(attributeName, attributeValue)))?.let { decode(it) }
        }

    /**
     * Writes [doc] whole under its user's key, applying the configured TTL.
     *
     * The SDK calls this from [coroutineScope] after an evaluation, so a failure is reported to
     * `onError` and dropped rather than thrown — see the class documentation for the write race
     * that sharing one store implies.
     */
    override suspend fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        failOpen(Unit) {
            val key = redisKey(docKey(doc.attributeName, doc.attributeValue))
            commands.set(key, json.encodeToString(doc.toRedisDocument()), ttlSeconds)
        }
    }

    /**
     * Loads every user's document in one `MGET` — the multi-query the interface leaves to Redis-like
     * services, instead of one round trip per attribute.
     *
     * @param attributes attribute name to value, as the SDK holds them
     * @return the documents that were found, keyed `name||value`; absent, unparsable and — on a
     *   Redis failure — all keys are simply left out
     */
    override suspend fun getAllAssignments(
        attributes: Map<String, String>
    ): Map<String, GBStickyAssignmentsDocument> {
        if (attributes.isEmpty()) return emptyMap()

        val docKeys = attributes.map { (name, value) -> docKey(name, value) }
        val payloads = failOpen(emptyList()) { commands.mget(docKeys.map(::redisKey)) }

        val documents = mutableMapOf<String, GBStickyAssignmentsDocument>()
        docKeys.forEachIndexed { index, docKey ->
            val payload = payloads.getOrNull(index) ?: return@forEachIndexed

            // Per document, so one corrupt payload cannot drop the whole batch.
            val document = failOpen(null) {
                decode(payload)
            } ?: return@forEachIndexed
            documents[docKey] = document
        }
        return documents
    }

    /**
     * The key the SDK indexes its sticky documents by. It is also the un-prefixed Redis key, which
     * is what keeps the layout wire-compatible with the other SDKs' Redis services.
     */
    private fun docKey(attributeName: String, attributeValue: String): String =
        "$attributeName$KEY_SEPARATOR$attributeValue"

    /** Namespaces [docKey] for Redis; a no-op with the default (empty) prefix. */
    private fun redisKey(docKey: String): String = "$keyPrefix$docKey"

    /** Binds this service's `onError` to the shared helper, so call sites stay readable. */
    private suspend fun <T> failOpen(fallback: T, block: suspend () -> T): T =
        failOpen(fallback, onError, block)

    /**
     * Parses a stored payload. Throws on malformed JSON, which the surrounding [failOpen] turns
     * into a miss.
     */
    private fun decode(payload: String): GBStickyAssignmentsDocument? =
        json.decodeFromString(GBRedisStickyDocument.serializer(), payload).toGBDocument()

    companion object {

        /** Separator between attribute name and value; matches the SDK's internal key format. */
        const val KEY_SEPARATOR: String = "||"

        /** Empty for wire-compatibility with the TypeScript SDK's `RedisStickyBucketService`. */
        const val DEFAULT_KEY_PREFIX: String = ""

        /**
         * No expiry, matching the TypeScript Redis service, which writes with a plain `SET`.
         *
         * The reason is that an assignment must outlive any single process, so a user keeps the
         * same variation across restarts and across a scaled fleet. The cost is unbounded growth
         * where identifiers are anonymous or high-cardinality — set a `ttl` there and accept that
         * a returning user past it gets rebucketed. Note that in a fleet shared with the
         * TypeScript service, its `SET` without an expiry clears any TTL this service applied.
         */
        val DEFAULT_TTL: Duration? = null

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The document as it sits in Redis.
 *
 * [GBStickyAssignmentsDocument] is `@Serializable` and could be read directly, but its
 * `attributeValue` is a `String`, while the TypeScript service writes whatever JSON primitive the
 * attribute held. Typing the field as a [JsonPrimitive] lets a numeric or boolean id written by it
 * round-trip instead of failing to parse.
 */
@Serializable
private data class GBRedisStickyDocument(
    val attributeName: String,
    val attributeValue: JsonPrimitive,
    val assignments: Map<String, String>
)

/**
 * Converts a stored document to the SDK's type.
 *
 * @return `null` for a `null` attribute value: `JsonNull.content` is the string `"null"`, which
 *   would look like a legitimate identifier and bucket every such user together
 */
private fun GBRedisStickyDocument.toGBDocument(): GBStickyAssignmentsDocument? {
    if (attributeValue is JsonNull) return null
    return GBStickyAssignmentsDocument(
        attributeName = attributeName,
        attributeValue = attributeValue.content,
        assignments = assignments
    )
}

/**
 * Converts an SDK document for storage. The attribute value is always written as a JSON string,
 * matching how the SDK itself holds it.
 */
private fun GBStickyAssignmentsDocument.toRedisDocument(): GBRedisStickyDocument =
    GBRedisStickyDocument(
        attributeName = attributeName,
        attributeValue = JsonPrimitive(attributeValue),
        assignments = assignments,
    )
