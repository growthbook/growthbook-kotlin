package com.sdk.growthbook.caffeine.stickybucket

import com.sdk.growthbook.caffeine.FakeTicker
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class GBCaffeineStickyBucketServiceTests {

    private val scope = TestScope()

    private fun service(
        clientKey: String = CLIENT_KEY,
        maximumSize: Long = GBCaffeineStickyBucketService.DEFAULT_MAXIMUM_SIZE,
        expireAfterWrite: Duration? = null,
        expireAfterAccess: Duration? = null,
        recordStats: Boolean = false,
        ticker: FakeTicker? = null
    ) = GBCaffeineStickyBucketService(
        coroutineScope = scope,
        clientKey = clientKey,
        maximumSize = maximumSize,
        expireAfterWrite = expireAfterWrite,
        expireAfterAccess = expireAfterAccess,
        recordStats = recordStats,
        ticker = ticker
    )

    private fun document(
        attributeValue: String,
        attributeName: String = "id",
        assignments: Map<String, String> = mapOf("exp-1__0" to "control")
    ) = GBStickyAssignmentsDocument(
        attributeName = attributeName,
        attributeValue = attributeValue,
        assignments = assignments
    )

    @Test
    fun `a user with no assignments is a miss`() = scope.runTest {
        assertNull(service().getAssignments("id", USER))
    }

    @Test
    fun `a saved document is read back`() = scope.runTest {
        val service = service()
        val doc = document(USER)

        service.saveAssignments(doc)

        assertEquals(doc, service.getAssignments("id", USER))
    }

    @Test
    fun `saving again replaces the document`() = scope.runTest {
        val service = service()
        service.saveAssignments(document(USER))

        val merged = document(USER, assignments = mapOf("exp-1__0" to "variant"))
        service.saveAssignments(merged)

        assertEquals(merged, service.getAssignments("id", USER))
    }

    /** Sticky documents are keyed by attribute *and* value, so two identifiers never collide. */
    @Test
    fun `documents of different attributes are kept apart`() = scope.runTest {
        val service = service()
        val byId = document(USER, attributeName = "id")
        val byDeviceId = document(USER, attributeName = "device_id")

        service.saveAssignments(byId)
        service.saveAssignments(byDeviceId)

        assertEquals(byId, service.getAssignments("id", USER))
        assertEquals(byDeviceId, service.getAssignments("device_id", USER))
    }

    @Test
    fun `getAllAssignments returns the documents it finds, keyed as the SDK indexes them`() =
        scope.runTest {
            val service = service()
            val doc = document(USER)
            service.saveAssignments(doc)

            val found = service.getAllAssignments(
                mapOf("id" to USER, "device_id" to "device-1")
            )

            // The unknown device_id is simply left out rather than mapped to null.
            assertEquals(mapOf("id||$USER" to doc), found)
        }

    @Test
    fun `getAllAssignments without attributes does no lookup`() = scope.runTest {
        assertEquals(emptyMap(), service().getAllAssignments(emptyMap()))
    }

    /**
     * The client key namespaces the *stored* key, never the one handed back: the SDK looks its
     * documents up by `name||value`, so prefixing what `getAllAssignments` returns would make
     * every sticky lookup miss.
     */
    @Test
    fun `the client key does not leak into the returned keys`() = scope.runTest {
        val service = service(clientKey = "some-api-key")
        val doc = document(USER)
        service.saveAssignments(doc)

        val found = service.getAllAssignments(mapOf("id" to USER))

        assertEquals(setOf("id||$USER"), found.keys)
    }

    /**
     * Two SDK instances each get their own service; the stored keys carry the client key, so the
     * documents of one are not visible to the other even for the same user.
     */
    @Test
    fun `services of different client keys do not see each other's documents`() = scope.runTest {
        val production = service(clientKey = "key-prod")
        val staging = service(clientKey = "key-staging")
        production.saveAssignments(document(USER))

        assertNull(staging.getAssignments("id", USER))
    }

    @Test
    fun `the entry count bound evicts once it is exceeded`() = scope.runTest {
        val service = service(maximumSize = 1)

        service.saveAssignments(document("user-a"))
        service.saveAssignments(document("user-b"))
        // Caffeine evicts asynchronously; this applies the pending maintenance now.
        service.cleanUp()

        // Which user survives is up to Caffeine's admission policy — the bound is the claim.
        val present = listOf("user-a", "user-b").count { service.getAssignments("id", it) != null }
        assertEquals(1, present)
    }

    @Test
    fun `expireAfterWrite drops assignments that are still being read`() = scope.runTest {
        val ticker = FakeTicker()
        val service = service(expireAfterWrite = 10.minutes, ticker = ticker)
        service.saveAssignments(document(USER))

        ticker.advance(9.minutes)
        assertEquals(document(USER), service.getAssignments("id", USER))

        ticker.advance(2.minutes)
        assertNull(service.getAssignments("id", USER))
    }

    @Test
    fun `expireAfterAccess keeps an active user sticky and lets an idle one fall out`() =
        scope.runTest {
            val ticker = FakeTicker()
            val service = service(expireAfterAccess = 10.minutes, ticker = ticker)
            service.saveAssignments(document(USER))
            service.saveAssignments(document(IDLE_USER))

            // Only USER is read, so only their idle timer restarts.
            repeat(3) {
                ticker.advance(9.minutes)
                assertEquals(document(USER), service.getAssignments("id", USER))
            }

            assertNull(service.getAssignments("id", IDLE_USER))
        }

    @Test
    fun `clear rebuckets everyone`() = scope.runTest {
        val service = service()
        service.saveAssignments(document("user-a"))
        service.saveAssignments(document("user-b"))

        service.clear()

        assertNull(service.getAssignments("id", "user-a"))
        assertNull(service.getAssignments("id", "user-b"))
    }

    @Test
    fun `statistics are zero and flagged as such unless recording was enabled`() = scope.runTest {
        val service = service()
        service.saveAssignments(document(USER))
        service.getAssignments("id", USER)

        val stats = service.stats()

        assertFalse(stats.isRecording)
        assertEquals(0, stats.hitCount)
    }

    @Test
    fun `statistics count hits and misses when recording is enabled`() = scope.runTest {
        val service = service(recordStats = true)
        service.saveAssignments(document(USER))

        service.getAssignments("id", USER)
        service.getAssignments("id", "absent-user")

        val stats = service.stats()
        assertTrue(stats.isRecording)
        assertEquals(1, stats.hitCount)
        assertEquals(1, stats.missCount)
    }

    @Test
    fun `a non-positive bound fails where it is configured`() {
        assertFailsWith<IllegalArgumentException> { service(maximumSize = 0) }
        assertFailsWith<IllegalArgumentException> { service(expireAfterWrite = 0.minutes) }
        assertFailsWith<IllegalArgumentException> { service(expireAfterAccess = (-1).minutes) }
    }

    private companion object {
        const val CLIENT_KEY = "sdk-test-key"
        const val USER = "user-42"
        const val IDLE_USER = "user-7"
    }
}
