package com.sdk.growthbook.redis.stickybucket

import com.sdk.growthbook.redis.GBRedisCommands
import com.sdk.growthbook.redis.GBJedisRedisCommands
import com.sdk.growthbook.redis.GBLettuceRedisCommands
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.lettuce.core.RedisClient as LettuceClient
import redis.clients.jedis.RedisClient as JedisClient

/**
 * Exercises both adapters against a real Redis, which is the only place the client-specific
 * handling of an absent key can be proven: Jedis returns a `null` entry from MGET, Lettuce returns
 * a `KeyValue` without a value. The fake in [GBRedisStickyBucketServiceTests] cannot show that.
 *
 * Self-skipping: the whole class is ignored unless Redis answers at `GB_TEST_REDIS_URL`
 * (default `redis://localhost:6379`), so CI needs neither Docker nor a Redis service.
 *
 * Every key is namespaced with a per-run UUID prefix and deleted afterwards, so pointing this at a
 * shared Redis cannot disturb existing data.
 */
class GBRedisStickyBucketServiceIntegrationTests {

    private val url = System.getenv("GB_TEST_REDIS_URL") ?: "redis://localhost:6379"
    private val keyPrefix = "gb-kotlin-it:${UUID.randomUUID()}:"
    private val scope = TestScope()
    private val errors = mutableListOf<Throwable>()

    private var jedis: JedisClient? = null
    private var lettuceClient: LettuceClient? = null
    private var lettuceConnection: StatefulRedisConnection<String, String>? = null

    /**
     * Connects both clients, or skips the whole class when Redis is not there — including the
     * reason, so a genuinely broken connection is not mistaken for "no Redis configured".
     */
    @Before
    fun connectOrSkip() {
        val failure = try {
            // Jedis first: it fails fast on a refused connection, whereas Lettuce would sit on
            // its default connect timeout before giving up.
            jedis = JedisClient.create(URI(url)).also { it.ping() }
            lettuceClient = LettuceClient.create(url)
            lettuceConnection = lettuceClient?.connect()
            null
        } catch (error: Throwable) {
            disconnect()
            // Reported, not discarded: a bad URL scheme, an auth failure or a Lettuce connect
            // error would otherwise leave this suite reporting "skipped" forever with no clue why,
            // and it is the only place cross-client MGET behaviour is proven.
            "${error::class.java.simpleName}: ${error.message}"
        }
        assumeTrue(
            "Redis is not reachable at $endpoint — skipping integration test ($failure)",
            failure == null
        )
    }

    /**
     * Host and port only. `GB_TEST_REDIS_URL` may carry credentials (`redis://user:pass@host`),
     * and a skip message ends up in the test report and CI log.
     */
    private val endpoint: String
        get() = runCatching { URI(url).let { "${it.host}:${it.port}" } }.getOrDefault("GB_TEST_REDIS_URL")

    /**
     * Deletes this run's keys and closes both clients. The scan is bounded by the per-run prefix,
     * so nothing else in a shared Redis is touched.
     */
    @After
    fun cleanUp() {
        jedis?.let { client ->
            val keys = client.keys("$keyPrefix*")
            if (keys.isNotEmpty()) client.del(*keys.toTypedArray())
        }
        disconnect()
    }

    /** Closes whatever was opened; safe to call from a half-finished [connectOrSkip]. */
    private fun disconnect() {
        lettuceConnection?.close()
        lettuceClient?.shutdown()
        jedis?.close()
        lettuceConnection = null
        lettuceClient = null
        jedis = null
    }

    /** A service namespaced to this run's prefix, collecting failures in [errors]. */
    private fun service(commands: GBRedisCommands) =
        GBRedisStickyBucketService(commands, scope, keyPrefix) { errors += it }

    /** A service over the real Jedis client; the connection is asserted by [connectOrSkip]. */
    private fun jedisService() = service(GBJedisRedisCommands(jedis!!))

    /** A service over the real Lettuce connection; asserted by [connectOrSkip]. */
    private fun lettuceService() = service(GBLettuceRedisCommands(lettuceConnection!!))

    /** A document carrying one assignment, identified by the given attribute. */
    private fun document(attributeName: String, attributeValue: String) =
        GBStickyAssignmentsDocument(
            attributeName = attributeName,
            attributeValue = attributeValue,
            assignments = mapOf("exp-1__0" to "control")
        )

    @Test
    fun `a document written with Jedis is readable with Lettuce and back`() = scope.runTest {
        val fromJedis = document("id", "jedis-user")
        val fromLettuce = document("id", "lettuce-user")

        jedisService().saveAssignments(fromJedis)
        lettuceService().saveAssignments(fromLettuce)

        // Both adapters must agree on the stored bytes, or a fleet running a mix of clients
        // would split users across two sets of assignments.
        assertEquals(fromJedis, lettuceService().getAssignments("id", "jedis-user"))
        assertEquals(fromLettuce, jedisService().getAssignments("id", "lettuce-user"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `getAssignments returns null for an absent key with Jedis`() = scope.runTest {
        assertNull(jedisService().getAssignments("id", "absent"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `getAssignments returns null for an absent key with Lettuce`() = scope.runTest {
        assertNull(lettuceService().getAssignments("id", "absent"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `getAllAssignments keeps real MGET results aligned with Jedis`() = scope.runTest {
        assertAlignedAcrossHoles(jedisService())
    }

    @Test
    fun `getAllAssignments keeps real MGET results aligned with Lettuce`() = scope.runTest {
        assertAlignedAcrossHoles(lettuceService())
    }

    /**
     * Stores the first and last of three attributes, then asserts the missing middle one neither
     * fails the batch nor shifts the other documents onto the wrong key.
     */
    private suspend fun assertAlignedAcrossHoles(service: GBRedisStickyBucketService) {
        val first = document("id", "u1")
        val third = document("companyId", "u3")
        service.saveAssignments(first)
        service.saveAssignments(third)

        val result = service.getAllAssignments(
            mapOf("id" to "u1", "deviceId" to "u2", "companyId" to "u3")
        )

        assertEquals(mapOf("id||u1" to first, "companyId||u3" to third), result)
        assertTrue(errors.isEmpty())
    }
}
