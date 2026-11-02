package com.sdk.growthbook.redis.cache

import com.sdk.growthbook.redis.FakeRedisCommands
import com.sdk.growthbook.redis.GBRedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Covers the layer against [FakeRedisCommands]: that reads are served from memory without a round
 * trip, that writes reach Redis in the background and collapse when they pile up, that a key
 * outside the layer's own scope is refused, and that no Redis failure escapes to the caller.
 *
 * The client-specific behaviour these tests cannot show — how each adapter reports an absent key —
 * is covered by the integration tests instead.
 */
class GBRedisCachingLayerTests {

    private val errors = mutableListOf<Throwable>()
    private val writerScopes = mutableListOf<CoroutineScope>()

    /**
     * The layer's writer runs until its scope is cancelled, so it gets a scope of its own rather
     * than the test's: sharing `testScheduler` keeps [advanceUntilIdle] in control of it, while
     * staying outside the test's job tree means `runTest` does not wait for a loop that never ends.
     * ([TestScope.backgroundScope] looks like the right tool but is not driven by
     * [advanceUntilIdle] — verified.)
     */
    private fun TestScope.writerScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler)).also { writerScopes += it }

    @AfterTest
    fun stopWriters() = writerScopes.forEach { it.cancel() }

    /** A layer whose writer is driven by the test scheduler and whose failures land in [errors]. */
    private fun TestScope.layer(
        commands: GBRedisCommands,
        keyPrefix: String = GBRedisCachingLayer.DEFAULT_KEY_PREFIX,
        ttl: Duration? = GBRedisCachingLayer.DEFAULT_TTL,
        onError: (Throwable) -> Unit = { errors += it }
    ) = GBRedisCachingLayer(commands, writerScope(), CLIENT_KEY, keyPrefix, ttl, onError)

    // region reads never hit Redis

    @Test
    fun `getContent misses before warm up and issues no Redis command`() = runTest {
        val commands = FakeRedisCommands()
        commands.store[FEATURE_CACHE] = """{"features":{}}"""

        val layer = layer(commands)

        // The value is in Redis, but nothing has been warmed up yet.
        assertNull(layer.getContent(FEATURE_CACHE))
        assertTrue(commands.getCalls.isEmpty())
        assertTrue(commands.mgetCalls.isEmpty())
    }

    @Test
    fun `getContent serves the warmed up payload without touching Redis again`() = runTest {
        val payload = """{"features":{"flag":{"defaultValue":true}}}"""
        val commands = FakeRedisCommands()
        commands.store[FEATURE_CACHE] = payload
        val layer = layer(commands)

        layer.warmUp()
        val before = commands.getCalls.size

        assertEquals(payload, layer.getContent(FEATURE_CACHE))
        assertEquals(payload, layer.getContent(FEATURE_CACHE))
        // getContent runs inside the SDK's synchronous initialize(); it must stay a memory read.
        assertEquals(before, commands.getCalls.size)
        assertTrue(commands.mgetCalls.isEmpty())
    }

    // endregion

    // region warm up

    @Test
    fun `warmUp reads the file name the SDK asks for in a single round trip`() = runTest {
        val commands = FakeRedisCommands()
        commands.store[FEATURE_CACHE] = """{"features":{}}"""
        val layer = layer(commands)

        layer.warmUp()

        assertEquals(listOf(FEATURE_CACHE), commands.getCalls)
        assertEquals("""{"features":{}}""", layer.getContent(FEATURE_CACHE))
    }

    @Test
    fun `warmUp leaves the cache empty when Redis has no entry`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        layer.warmUp()

        assertNull(layer.getContent(FEATURE_CACHE))
        assertTrue(errors.isEmpty())
    }

    // endregion

    // region writes

    @Test
    fun `saveContent is readable immediately and reaches Redis in the background`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        layer.saveContent(FEATURE_CACHE, "payload")

        // Visible before the writer runs: the SDK may read straight after writing.
        assertEquals("payload", layer.getContent(FEATURE_CACHE))
        assertNull(commands.store[FEATURE_CACHE])

        advanceUntilIdle()

        assertEquals("payload", commands.store[FEATURE_CACHE])
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `keyPrefix namespaces the Redis key but not the file name`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands, keyPrefix = "gb:cache:")

        layer.saveContent(FEATURE_CACHE, "payload")
        advanceUntilIdle()

        assertEquals(setOf("gb:cache:$FEATURE_CACHE"), commands.store.keys)
        // The SDK only ever knows the unprefixed name.
        assertEquals("payload", layer.getContent(FEATURE_CACHE))

        layer.warmUp()
        assertEquals(listOf("gb:cache:$FEATURE_CACHE"), commands.getCalls)
    }

    @Test
    fun `the payload is written without an expiry by default`() = runTest {
        val commands = FakeRedisCommands()

        layer(commands).saveContent(FEATURE_CACHE, "payload")
        advanceUntilIdle()

        assertNull(commands.ttls.getValue(FEATURE_CACHE))
    }

    @Test
    fun `a ttl is applied to the payload write, in whole seconds`() = runTest {
        val commands = FakeRedisCommands()

        layer(commands, ttl = 10.minutes).saveContent(FEATURE_CACHE, "payload")
        advanceUntilIdle()

        assertEquals(600L, commands.ttls.getValue(FEATURE_CACHE))
    }

    @Test
    fun `a ttl below one second is rejected where it is configured`() = runTest {
        // Redis rejects EX 0, so truncating to zero seconds must fail at construction rather than
        // turning every background write into an error.
        assertFailsWith<IllegalArgumentException> {
            layer(FakeRedisCommands(), ttl = 500.milliseconds)
        }
    }

    @Test
    fun `writes queued together collapse into one push of the newest payload`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        layer.saveContent(FEATURE_CACHE, "v1")
        layer.saveContent(FEATURE_CACHE, "v2")
        layer.saveContent(FEATURE_CACHE, "v3")
        advanceUntilIdle()

        // One writer pushing the current payload, rather than a coroutine per call racing to
        // Redis — so a superseded payload can never be the one that lands last.
        assertEquals(listOf(FEATURE_CACHE to "v3"), commands.setCalls)
    }

    @Test
    fun `a write after the queue drained still reaches Redis`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        layer.saveContent(FEATURE_CACHE, "v1")
        advanceUntilIdle()
        layer.saveContent(FEATURE_CACHE, "v2")
        advanceUntilIdle()

        // Guards against a lost wake-up: the writer must not go idle holding a stale payload.
        assertEquals(listOf(FEATURE_CACHE to "v1", FEATURE_CACHE to "v2"), commands.setCalls)
    }

    // endregion

    // region scope

    @Test
    fun `a key outside the feature cache is refused rather than served from memory`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        // What the default sticky bucket service would ask for once this layer is passed to
        // setCachingLayer(): writing it would strand assignments in Redis, unread forever.
        layer.saveContent(STICKY_KEY, """{"attributeName":"id","attributeValue":"u1"}""")
        advanceUntilIdle()

        assertNull(layer.getContent(STICKY_KEY))
        assertTrue(commands.store.isEmpty())
    }

    @Test
    fun `an out of scope key is reported once, as a scope exception`() = runTest {
        val layer = layer(FakeRedisCommands())

        layer.saveContent(STICKY_KEY, "{}")
        layer.getContent(STICKY_KEY)
        layer.getContent("gbStickyBuckets__$CLIENT_KEY" + "_id||u2")

        val reported = assertIs<GBRedisCacheScopeException>(errors.single())
        // The key embeds the attribute value identifying the user — it must not be reported.
        assertTrue(reported.message?.contains("u1") != true)
        assertTrue(reported.message?.contains("GBRedisStickyBucketService") == true)
    }

    @Test
    fun `the feature cache keeps working after an out of scope key`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands)

        layer.saveContent(STICKY_KEY, "{}")
        layer.saveContent(FEATURE_CACHE, "payload")
        advanceUntilIdle()

        assertEquals("payload", layer.getContent(FEATURE_CACHE))
        assertEquals(mapOf(FEATURE_CACHE to "payload"), commands.store)
    }

    // endregion

    // region failures

    @Test
    fun `warmUp degrades to a miss when Redis fails`() = runTest {
        val failure = IllegalStateException("connection refused")
        val commands = FakeRedisCommands().apply { failGetWith = failure }
        val layer = layer(commands)

        layer.warmUp()

        assertNull(layer.getContent(FEATURE_CACHE))
        assertSame(failure, errors.single())
    }

    @Test
    fun `saveContent keeps the value in memory when Redis fails`() = runTest {
        val failure = IllegalStateException("read-only replica")
        val commands = FakeRedisCommands().apply { failSetWith = failure }
        val layer = layer(commands)

        layer.saveContent(FEATURE_CACHE, "payload")
        advanceUntilIdle()

        // A failed push must not lose the payload for this instance, nor cancel the scope.
        assertEquals("payload", layer.getContent(FEATURE_CACHE))
        assertSame(failure, errors.single())
    }

    @Test
    fun `a throwing onError callback does not break fail-open`() = runTest {
        val commands = FakeRedisCommands().apply {
            failGetWith = IllegalStateException("down")
            failSetWith = IllegalStateException("read-only replica")
        }
        val layer = layer(commands) { throw IllegalStateException("consumer callback blew up") }

        // Neither may throw: warmUp is awaited on the caller's startup path, and the push runs in
        // the caller's scope, where an escaping exception would cancel it.
        layer.warmUp()
        layer.saveContent(FEATURE_CACHE, "v1")
        advanceUntilIdle()

        assertEquals("v1", layer.getContent(FEATURE_CACHE))

        // The writer survived the bad callback, so later writes still reach Redis.
        commands.failSetWith = null
        layer.saveContent(FEATURE_CACHE, "v2")
        advanceUntilIdle()

        assertEquals("v2", commands.store[FEATURE_CACHE])
    }

    @Test
    fun `a throwing onError callback does not break the out of scope report`() = runTest {
        val commands = FakeRedisCommands()
        val layer = layer(commands) { throw IllegalStateException("consumer callback blew up") }

        layer.saveContent(STICKY_KEY, "{}")

        assertNull(layer.getContent(STICKY_KEY))
        assertTrue(commands.setCalls.isEmpty())
    }

    @Test
    fun `warmUp lets CancellationException through without reporting it`() = runTest {
        val commands =
            FakeRedisCommands().apply { failGetWith = CancellationException("cancelled") }
        val layer = layer(commands)

        assertFailsWith<CancellationException> {
            layer.warmUp()
        }
        assertTrue(errors.isEmpty())
    }

    // endregion

    private companion object {
        const val CLIENT_KEY = "sdk-abc123"
        const val FEATURE_CACHE = "FeatureCache_$CLIENT_KEY"
        const val STICKY_KEY = "gbStickyBuckets__${CLIENT_KEY}_id||u1"
    }
}
