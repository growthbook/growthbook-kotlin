package com.sdk.growthbook.redis.stickybucket

import com.sdk.growthbook.redis.FakeRedisCommands
import com.sdk.growthbook.redis.GBRedisCommands
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Covers the service against [FakeRedisCommands]: the key layout and JSON shape it shares with the
 * TypeScript Redis service, that `getAllAssignments` is a single `MGET`, the configured TTL,
 * and that a Redis failure or a corrupt payload degrades to a miss instead of throwing.
 *
 * How each client reports an absent key is covered by [GBRedisStickyBucketServiceIntegrationTests],
 * which the fake cannot stand in for.
 */
class GBRedisStickyBucketServiceTests {

    private val scope = TestScope()
    private val errors = mutableListOf<Throwable>()

    /** A service over [commands] whose failures land in [errors] instead of being reported. */
    private fun service(
        commands: GBRedisCommands,
        keyPrefix: String = GBRedisStickyBucketService.DEFAULT_KEY_PREFIX,
        ttl: Duration? = GBRedisStickyBucketService.DEFAULT_TTL
    ) = GBRedisStickyBucketService(commands, scope, keyPrefix, ttl) { errors += it }

    /** A document with defaults for whatever a test does not care about. */
    private fun document(
        attributeValue: String = "user-1",
        attributeName: String = "id",
        assignments: Map<String, String> = mapOf("exp-1__0" to "control")
    ) = GBStickyAssignmentsDocument(attributeName, attributeValue, assignments)

    @Test
    fun `saveAssignments then getAssignments round-trips the document`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands)
        val doc = document(
            attributeName = "deviceId",
            attributeValue = "device-abc",
            assignments = mapOf("exp-1__0" to "control", "exp-2__1" to "variant")
        )

        service.saveAssignments(doc)
        val result = service.getAssignments("deviceId", "device-abc")

        assertEquals(doc, result)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `saveAssignments stores the document shape shared with the other SDKs`() = scope.runTest {
        val commands = FakeRedisCommands()

        service(commands).saveAssignments(document(attributeValue = "123"))

        val stored = Json.parseToJsonElement(commands.store.getValue("id||123")).jsonObject
        assertEquals(
            setOf("attributeName", "attributeValue", "assignments"),
            stored.keys
        )
        assertEquals("id", stored.getValue("attributeName").jsonPrimitive.content)
        assertEquals("123", stored.getValue("attributeValue").jsonPrimitive.content)
        // A numeric attributeValue would still round-trip through our own decoder, but the
        // TypeScript service writes a string — keep it a string on the wire.
        assertTrue(stored.getValue("attributeValue").jsonPrimitive.isString)
        assertEquals(
            mapOf("exp-1__0" to "control"),
            stored.getValue("assignments").jsonObject.mapValues { it.value.jsonPrimitive.content }
        )
    }

    @Test
    fun `keyPrefix namespaces the Redis key but never the returned document key`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands, keyPrefix = "gb:sticky:")
        val doc = document(attributeValue = "user-1")

        service.saveAssignments(doc)

        assertEquals(setOf("gb:sticky:id||user-1"), commands.store.keys)

        val all = service.getAllAssignments(mapOf("id" to "user-1"))

        // The SDK looks documents up by the unprefixed key, so a prefix must not leak out here.
        assertEquals(mapOf("id||user-1" to doc), all)
        assertEquals(listOf(listOf("gb:sticky:id||user-1")), commands.mgetCalls)
    }

    @Test
    fun `a throwing onError callback does not break fail-open`() = scope.runTest {
        val failure = IllegalStateException("connection refused")
        val commands = FakeRedisCommands().apply {
            failGetWith = failure
            failSetWith = failure
            failMgetWith = failure
        }
        val service = GBRedisStickyBucketService(commands, scope) {
            throw IllegalStateException("consumer callback blew up")
        }

        // saveAssignments runs in the consumer's scope from a fire-and-forget launch, and the
        // reads sit on the evaluation path — a callback that throws must not escape either.
        service.saveAssignments(document())
        assertNull(service.getAssignments("id", "user-1"))
        assertEquals(emptyMap(), service.getAllAssignments(mapOf("id" to "user-1")))
    }

    @Test
    fun `assignments are written without an expiry by default`() = scope.runTest {
        val commands = FakeRedisCommands()

        service(commands).saveAssignments(document(attributeValue = "user-1"))

        // Matches the TypeScript service: an assignment must outlive any one process.
        assertNull(commands.ttls.getValue("id||user-1"))
    }

    @Test
    fun `a ttl is applied to every assignment write, in whole seconds`() = scope.runTest {
        val commands = FakeRedisCommands()

        service(commands, ttl = 180.days).saveAssignments(document(attributeValue = "user-1"))

        assertEquals(180L * 24 * 60 * 60, commands.ttls.getValue("id||user-1"))
    }

    @Test
    fun `a ttl below one second is rejected where it is configured`() {
        // Redis rejects EX 0, so truncating to zero seconds must fail at construction rather than
        // turning every background write into an error.
        assertFailsWith<IllegalArgumentException> {
            service(FakeRedisCommands(), ttl = 500.milliseconds)
        }
        assertFailsWith<IllegalArgumentException> {
            service(FakeRedisCommands(), ttl = Duration.ZERO)
        }
    }

    @Test
    fun `getAllAssignments fetches every attribute in a single round trip`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands)
        val attributes = mapOf("id" to "u1", "deviceId" to "u2", "companyId" to "u3")
        attributes.forEach { (name, value) ->
            service.saveAssignments(document(attributeName = name, attributeValue = value))
        }

        val result = service.getAllAssignments(attributes)

        assertEquals(3, result.size)
        // One MGET, not a GET per attribute — that is the whole point of overriding this method.
        assertEquals(1, commands.mgetCalls.size)
        assertEquals(3, commands.mgetCalls.single().size)
        assertTrue(commands.getCalls.isEmpty())
    }

    @Test
    fun `getAllAssignments keeps documents aligned when one attribute is missing`() =
        scope.runTest {
            val commands = FakeRedisCommands()
            val service = service(commands)
            val first = document(attributeName = "id", attributeValue = "u1")
            val third = document(
                attributeName = "companyId",
                attributeValue = "u3",
                assignments = mapOf("exp-9__0" to "variant")
            )
            service.saveAssignments(first)
            service.saveAssignments(third)

            val result = service.getAllAssignments(
                mapOf("id" to "u1", "deviceId" to "u2", "companyId" to "u3")
            )

            // The hole in the middle must not shift u3's document onto u2's key.
            assertEquals(mapOf("id||u1" to first, "companyId||u3" to third), result)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun `getAllAssignments makes no Redis call for empty attributes`() = scope.runTest {
        val commands = FakeRedisCommands()

        val result = service(commands).getAllAssignments(emptyMap())

        assertEquals(emptyMap(), result)
        // MGET without arguments is a Redis error, so the call has to be skipped entirely.
        assertTrue(commands.mgetCalls.isEmpty())
    }

    @Test
    fun `getAllAssignments skips only the corrupt document and reports it`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands)
        val first = document(attributeName = "id", attributeValue = "u1")
        val third = document(attributeName = "companyId", attributeValue = "u3")
        service.saveAssignments(first)
        service.saveAssignments(third)
        commands.store["deviceId||u2"] = "{\"attributeName\": broken"

        val result = service.getAllAssignments(
            mapOf("id" to "u1", "deviceId" to "u2", "companyId" to "u3")
        )

        assertEquals(mapOf("id||u1" to first, "companyId||u3" to third), result)
        assertEquals(1, errors.size)
    }

    @Test
    fun `getAllAssignments tolerates a client that returns fewer values than keys`() =
        scope.runTest {
            val backing = FakeRedisCommands()
            val doc = document(attributeName = "id", attributeValue = "u1")
            service(backing).saveAssignments(doc)

            // A third-party GBRedisCommands may drop trailing nulls; that must not crash or
            // shift values onto the wrong attribute.
            val truncating = object : GBRedisCommands by backing {
                override suspend fun mget(keys: List<String>): List<String?> =
                    backing.mget(keys).take(1)
            }

            val result = service(truncating).getAllAssignments(
                mapOf("id" to "u1", "deviceId" to "u2")
            )

            assertEquals(mapOf("id||u1" to doc), result)
        }

    // endregion

    // region foreign document shapes

    @Test
    fun `getAssignments reads a document written by another SDK`() = scope.runTest {
        val commands = FakeRedisCommands()
        // A numeric attributeValue and a field we do not know about: both must be accepted.
        commands.store["id||123"] = """
            {"attributeName":"id","attributeValue":123,
             "assignments":{"exp-1__0":"control"},"someFutureField":{"a":1}}
        """.trimIndent()

        val result = service(commands).getAssignments("id", "123")

        assertEquals(document(attributeName = "id", attributeValue = "123"), result)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `getAssignments treats a null attributeValue as a miss`() = scope.runTest {
        val commands = FakeRedisCommands()
        commands.store["id||u1"] =
            """{"attributeName":"id","attributeValue":null,"assignments":{}}"""

        val result = service(commands).getAssignments("id", "u1")

        assertNull(result)
    }

    @Test
    fun `getAssignments treats a document with a missing field as a miss and reports it`() =
        scope.runTest {
            val commands = FakeRedisCommands()
            commands.store["id||u1"] = """{"attributeName":"id","assignments":{}}"""

            val result = service(commands).getAssignments("id", "u1")

            assertNull(result)
            assertEquals(1, errors.size)
        }

    @Test
    fun `an absent key is a plain miss, not an error`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands)

        assertNull(service.getAssignments("id", "u1"))
        assertEquals(emptyMap(), service.getAllAssignments(mapOf("id" to "u1")))
        // Every new user misses once; reporting that would drown the consumer's callback.
        assertTrue(errors.isEmpty())
    }

    // endregion

    // region failures

    @Test
    fun `getAssignments degrades to a miss when Redis fails`() = scope.runTest {
        val failure = IllegalStateException("connection refused")
        val commands = FakeRedisCommands().apply { failGetWith = failure }

        val result = service(commands).getAssignments("id", "u1")

        assertNull(result)
        assertSame(failure, errors.single())
    }

    @Test
    fun `getAllAssignments degrades to an empty map when Redis fails`() = scope.runTest {
        val failure = IllegalStateException("connection refused")
        val commands = FakeRedisCommands().apply { failMgetWith = failure }

        val result = service(commands).getAllAssignments(mapOf("id" to "u1"))

        assertEquals(emptyMap(), result)
        assertSame(failure, errors.single())
    }

    @Test
    fun `saveAssignments swallows a Redis failure rather than cancelling the caller`() =
        scope.runTest {
            val failure = IllegalStateException("connection refused")
            val commands = FakeRedisCommands().apply { failSetWith = failure }

            // Called from a fire-and-forget coroutine on the evaluation path: a throw here
            // would take the consumer's scope down.
            service(commands).saveAssignments(document())

            assertSame(failure, errors.single())
        }

    @Test
    fun `a failing write leaves reads working`() = scope.runTest {
        val commands = FakeRedisCommands()
        val service = service(commands)
        service.saveAssignments(document(attributeValue = "u1"))
        commands.failSetWith = IllegalStateException("read-only replica")

        service.saveAssignments(document(attributeValue = "u2"))

        assertEquals(document(attributeValue = "u1"), service.getAssignments("id", "u1"))
        assertNull(service.getAssignments("id", "u2"))
    }

    @Test
    fun `getAssignments lets CancellationException through without reporting it`() = scope.runTest {
        val commands = FakeRedisCommands().apply { failGetWith = CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            service(commands).getAssignments("id", "u1")
        }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `getAllAssignments lets CancellationException through without reporting it`() =
        scope.runTest {
            val commands =
                FakeRedisCommands().apply { failMgetWith = CancellationException("cancelled") }

            assertFailsWith<CancellationException> {
                service(commands).getAllAssignments(mapOf("id" to "u1"))
            }
            assertTrue(errors.isEmpty())
        }
}
