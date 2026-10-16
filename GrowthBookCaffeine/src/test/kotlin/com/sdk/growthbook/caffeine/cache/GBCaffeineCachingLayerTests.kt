package com.sdk.growthbook.caffeine.cache

import com.sdk.growthbook.caffeine.FakeTicker
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class GBCaffeineCachingLayerTests {

    private val errors = mutableListOf<Throwable>()

    /** The key shape the SDK asks its caching layer for — `Constants.FEATURE_CACHE` plus a key. */
    private fun featureCacheKey(clientKey: String = CLIENT_KEY) = "FeatureCache_$clientKey"

    private fun layer(
        maximumSize: Long = GBCaffeineCachingLayer.DEFAULT_MAXIMUM_SIZE,
        maximumWeightBytes: Long? = null,
        expireAfterWrite: kotlin.time.Duration? = null,
        expireAfterAccess: kotlin.time.Duration? = null,
        recordStats: Boolean = false,
        ticker: FakeTicker? = null
    ) = GBCaffeineCachingLayer(
        maximumSize = maximumSize,
        maximumWeightBytes = maximumWeightBytes,
        expireAfterWrite = expireAfterWrite,
        expireAfterAccess = expireAfterAccess,
        recordStats = recordStats,
        ticker = ticker,
        onError = { errors += it }
    )

    @Test
    fun `an unwritten key is a miss rather than a failure`() {
        assertNull(layer().getContent(featureCacheKey()))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `a written payload is read back verbatim`() {
        val layer = layer()

        layer.saveContent(featureCacheKey(), PAYLOAD)

        assertEquals(PAYLOAD, layer.getContent(featureCacheKey()))
    }

    @Test
    fun `a rewritten key returns the latest payload`() {
        val layer = layer()

        layer.saveContent(featureCacheKey(), PAYLOAD)
        layer.saveContent(featureCacheKey(), OTHER_PAYLOAD)

        assertEquals(OTHER_PAYLOAD, layer.getContent(featureCacheKey()))
    }

    /** One layer can back several SDK instances, so client keys must not collide. */
    @Test
    fun `payloads of different client keys are kept apart`() {
        val layer = layer()

        layer.saveContent(featureCacheKey("key-a"), PAYLOAD)
        layer.saveContent(featureCacheKey("key-b"), OTHER_PAYLOAD)

        assertEquals(PAYLOAD, layer.getContent(featureCacheKey("key-a")))
        assertEquals(OTHER_PAYLOAD, layer.getContent(featureCacheKey("key-b")))
    }

    @Test
    fun `clear drops every payload`() {
        val layer = layer()
        layer.saveContent(featureCacheKey("key-a"), PAYLOAD)
        layer.saveContent(featureCacheKey("key-b"), PAYLOAD)

        layer.clear()

        assertNull(layer.getContent(featureCacheKey("key-a")))
        assertNull(layer.getContent(featureCacheKey("key-b")))
    }

    @Test
    fun `the entry count bound evicts once it is exceeded`() {
        val layer = layer(maximumSize = 1)

        layer.saveContent(featureCacheKey("key-a"), PAYLOAD)
        layer.saveContent(featureCacheKey("key-b"), PAYLOAD)
        // Caffeine evicts asynchronously; this applies the pending maintenance now.
        layer.cleanUp()

        // Which of the two survives is up to Caffeine's admission policy — the bound is the claim.
        assertEquals(1, presentCount(layer, "key-a", "key-b"))
    }

    @Test
    fun `the weight bound counts the payload's UTF-8 size`() {
        // Two payloads of 8 bytes each against a 10-byte bound: one of them has to go.
        val layer = layer(maximumWeightBytes = 10)

        layer.saveContent(featureCacheKey("key-a"), "12345678")
        layer.saveContent(featureCacheKey("key-b"), "12345678")
        layer.cleanUp()

        assertEquals(1, presentCount(layer, "key-a", "key-b"))
    }

    @Test
    fun `expireAfterWrite drops a payload that is still being read`() {
        val ticker = FakeTicker()
        val layer = layer(expireAfterWrite = 10.minutes, ticker = ticker)
        layer.saveContent(featureCacheKey(), PAYLOAD)

        ticker.advance(9.minutes)
        assertEquals(PAYLOAD, layer.getContent(featureCacheKey()))

        // The read above does not extend a write-based expiry.
        ticker.advance(2.minutes)
        assertNull(layer.getContent(featureCacheKey()))
    }

    @Test
    fun `expireAfterAccess is reset by a read`() {
        val ticker = FakeTicker()
        val layer = layer(expireAfterAccess = 10.minutes, ticker = ticker)
        layer.saveContent(featureCacheKey(), PAYLOAD)

        ticker.advance(9.minutes)
        assertEquals(PAYLOAD, layer.getContent(featureCacheKey()))

        // Still there: the read restarted the idle timer that a write-based expiry would ignore.
        ticker.advance(9.minutes)
        assertEquals(PAYLOAD, layer.getContent(featureCacheKey()))

        ticker.advance(11.minutes)
        assertNull(layer.getContent(featureCacheKey()))
    }

    @Test
    fun `an out of scope key is neither stored nor served`() {
        val layer = layer()

        layer.saveContent(STICKY_KEY, PAYLOAD)

        assertNull(layer.getContent(STICKY_KEY))
    }

    @Test
    fun `an out of scope key is reported once, whatever the traffic`() {
        val layer = layer()

        layer.saveContent(STICKY_KEY, PAYLOAD)
        layer.saveContent("${STICKY_KEY}_other", PAYLOAD)
        layer.getContent(STICKY_KEY)

        assertEquals(1, errors.size)
        assertTrue(errors.single() is GBCaffeineCacheScopeException)
    }

    /** The message ends up in consumer logs, and a sticky key embeds the user's attribute value. */
    @Test
    fun `the scope report names neither the key nor the user`() {
        val layer = layer()

        layer.saveContent(STICKY_KEY, PAYLOAD)

        val message = errors.single().message.orEmpty()
        assertFalse(message.contains(STICKY_KEY))
        assertFalse(message.contains(USER_ID))
    }

    /**
     * Without a callback the guard would otherwise be mute, and the misconfiguration it catches is
     * invisible: features keep working while sticky bucketing quietly does nothing.
     */
    @Test
    fun `a scope violation is logged when no callback was given`() {
        val logger = Logger.getLogger(GBCaffeineCachingLayer::class.java.name)
        val records = mutableListOf<LogRecord>()
        val collector = object : Handler() {
            override fun publish(record: LogRecord) { records += record }
            override fun flush() = Unit
            override fun close() = Unit
        }
        val usedParentHandlers = logger.useParentHandlers

        logger.addHandler(collector)
        // Keeps the expected warning out of the test output; restored below.
        logger.useParentHandlers = false
        try {
            GBCaffeineCachingLayer().saveContent(STICKY_KEY, PAYLOAD)
        } finally {
            logger.removeHandler(collector)
            logger.useParentHandlers = usedParentHandlers
        }

        assertEquals(1, records.size, "expected exactly one warning, got: $records")
        assertEquals(Level.WARNING, records.single().level)
    }

    /** `onError` is consumer code on the evaluation path: it must not take an evaluation down. */
    @Test
    fun `a throwing error callback does not escape`() {
        val layer = GBCaffeineCachingLayer(onError = { throw IllegalStateException("boom") })

        layer.saveContent(STICKY_KEY, PAYLOAD)

        assertNull(layer.getContent(STICKY_KEY))
    }

    @Test
    fun `statistics are zero and flagged as such unless recording was enabled`() {
        val layer = layer()
        layer.saveContent(featureCacheKey(), PAYLOAD)
        layer.getContent(featureCacheKey())
        layer.getContent(featureCacheKey("absent"))

        val stats = layer.stats()

        assertFalse(stats.isRecording)
        assertEquals(0, stats.hitCount)
        assertEquals(0, stats.missCount)
    }

    @Test
    fun `statistics count hits and misses when recording is enabled`() {
        val layer = layer(recordStats = true)
        layer.saveContent(featureCacheKey(), PAYLOAD)

        layer.getContent(featureCacheKey())
        layer.getContent(featureCacheKey("absent"))

        val stats = layer.stats()
        assertTrue(stats.isRecording)
        assertEquals(1, stats.hitCount)
        assertEquals(1, stats.missCount)
    }

    @Test
    fun `a non-positive bound fails where it is configured`() {
        assertFailsWith<IllegalArgumentException> { layer(maximumSize = 0) }
        assertFailsWith<IllegalArgumentException> { layer(maximumWeightBytes = 0) }
        assertFailsWith<IllegalArgumentException> { layer(expireAfterWrite = 0.minutes) }
        assertFailsWith<IllegalArgumentException> { layer(expireAfterAccess = (-1).minutes) }
    }

    /**
     * The SDK writes the cache from a background dispatcher while evaluation reads it on the app
     * thread, so concurrent access has to be safe and must not lose a key.
     */
    @Test
    fun `concurrent writes and reads keep every key`() {
        val layer = layer()
        val keyCount = 50
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(keyCount)

        repeat(keyCount) { index ->
            pool.execute {
                start.await()
                layer.saveContent(featureCacheKey("key-$index"), "payload-$index")
                layer.getContent(featureCacheKey("key-${index / 2}"))
                done.countDown()
            }
        }
        start.countDown()

        assertTrue(done.await(10, TimeUnit.SECONDS), "writers did not finish")
        pool.shutdown()
        repeat(keyCount) { index ->
            assertEquals("payload-$index", layer.getContent(featureCacheKey("key-$index")))
        }
    }

    private fun presentCount(layer: GBCaffeineCachingLayer, vararg clientKeys: String): Int =
        clientKeys.count { layer.getContent(featureCacheKey(it)) != null }

    private companion object {
        const val CLIENT_KEY = "sdk-test-key"
        const val USER_ID = "user-42"
        const val STICKY_KEY = "gbStickyBuckets__id||$USER_ID"
        const val PAYLOAD = """{"features":{"flag":{"defaultValue":true}}}"""
        const val OTHER_PAYLOAD = """{"features":{"flag":{"defaultValue":false}}}"""
    }
}
