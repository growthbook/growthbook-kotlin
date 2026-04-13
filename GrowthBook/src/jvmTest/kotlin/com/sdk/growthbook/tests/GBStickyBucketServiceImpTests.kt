package com.sdk.growthbook.tests

import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * CachingLayer fake that stores and retrieves content by file name,
 * so multiple keys can coexist in the same test.
 */
private class MapCachingLayer : CachingLayer {
    val store = mutableMapOf<String, JsonElement>()

    override fun getContent(fileName: String): JsonElement? = store[fileName]

    override fun saveContent(fileName: String, content: JsonElement) {
        store[fileName] = content
    }
}

/**
 * CachingLayer fake that always throws on getContent.
 */
private class ThrowingCachingLayer : CachingLayer {
    override fun getContent(fileName: String): JsonElement? =
        throw Exception("cache read error")

    override fun saveContent(fileName: String, content: JsonElement) {}
}

class GBStickyBucketServiceImpTests {

    private val scope = TestScope()
    private val prefix = "gbStickyBuckets__"

    @Test
    fun `getAssignments returns null when localStorage is null`() = scope.runTest {
        val service = GBStickyBucketServiceImp(scope, localStorage = null)

        val result = service.getAssignments("id", "user-1")

        assertNull(result)
    }

    @Test
    fun `getAssignments returns null when key is absent from cache`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)

        val result = service.getAssignments("id", "user-1")

        assertNull(result)
    }

    @Test
    fun `getAssignments returns document stored under the correct prefixed key`() = scope.runTest {
        val cache = MapCachingLayer()
        val doc = GBStickyAssignmentsDocument(
            attributeName = "id",
            attributeValue = "user-1",
            assignments = mapOf("exp-1" to "0")
        )
        val encoded = Json.parseToJsonElement(
            Json.encodeToString(
                GBStickyAssignmentsDocument.serializer(),
                doc
            )
        )
        cache.store["${prefix}id||user-1"] = encoded

        val service = GBStickyBucketServiceImp(scope, localStorage = cache)

        val result = service.getAssignments("id", "user-1")

        assertEquals(doc, result)
    }

    @Test
    fun `getAssignments returns null when cache entry is malformed JSON`() = scope.runTest {
        val brokenCache = object : CachingLayer {
            override fun getContent(fileName: String): JsonElement =
                Json.parseToJsonElement("\"not-an-object\"")

            override fun saveContent(fileName: String, content: JsonElement) {}
        }
        val service = GBStickyBucketServiceImp(scope, localStorage = brokenCache)

        val result = service.getAssignments("id", "user-1")

        assertNull(result)
    }

    @Test
    fun `getAssignments propagates exception when cache throws`() = scope.runTest {
        val service = GBStickyBucketServiceImp(scope, localStorage = ThrowingCachingLayer())

        assertFailsWith<Exception> {
            service.getAssignments("id", "user-1")
        }
    }

    @Test
    fun `saveAssignments does nothing when localStorage is null`() = scope.runTest {
        val service = GBStickyBucketServiceImp(scope, localStorage = null)
        service.saveAssignments(
            GBStickyAssignmentsDocument("id", "user-1", mapOf("exp-1" to "0"))
        )
    }

    @Test
    fun `saveAssignments persists document under the correct prefixed key`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)
        val doc = GBStickyAssignmentsDocument(
            attributeName = "id",
            attributeValue = "user-1",
            assignments = mapOf("exp-1" to "0")
        )

        service.saveAssignments(doc)

        val storedKey = "${prefix}id||user-1"
        val stored = cache.store[storedKey]
        val decoded = Json.decodeFromJsonElement(GBStickyAssignmentsDocument.serializer(), stored!!)
        assertEquals(doc, decoded)
    }

    @Test
    fun `saveAssignments then getAssignments round-trips the document`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)
        val doc = GBStickyAssignmentsDocument(
            attributeName = "deviceId",
            attributeValue = "device-abc",
            assignments = mapOf("feature-exp" to "1", "other-exp" to "0")
        )

        service.saveAssignments(doc)
        val result = service.getAssignments("deviceId", "device-abc")

        assertEquals(doc, result)
    }

    @Test
    fun `getAllAssignments returns empty map when localStorage is null`() = scope.runTest {
        val service = GBStickyBucketServiceImp(scope, localStorage = null)

        val result = service.getAllAssignments(mapOf("id" to "user-1"))

        assertEquals(emptyMap(), result)
    }

    @Test
    fun `getAllAssignments returns empty map when no attributes match cached keys`() =
        scope.runTest {
            val cache = MapCachingLayer()
            val service = GBStickyBucketServiceImp(scope, localStorage = cache)

            val result =
                service.getAllAssignments(mapOf("id" to "user-1", "deviceId" to "device-abc"))

            assertEquals(emptyMap(), result)
        }

    @Test
    fun `getAllAssignments returns all matching documents`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)

        val doc1 = GBStickyAssignmentsDocument("id", "user-1", mapOf("exp-1" to "0"))
        val doc2 = GBStickyAssignmentsDocument("deviceId", "device-abc", mapOf("exp-2" to "1"))

        service.saveAssignments(doc1)
        service.saveAssignments(doc2)

        val result = service.getAllAssignments(
            mapOf("id" to "user-1", "deviceId" to "device-abc")
        )

        assertEquals(2, result.size)
        assertEquals(doc1, result["id||user-1"])
        assertEquals(doc2, result["deviceId||device-abc"])
    }

    @Test
    fun `getAllAssignments skips attributes that have no cached document`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)

        val doc = GBStickyAssignmentsDocument("id", "user-1", mapOf("exp-1" to "0"))
        service.saveAssignments(doc)

        val result = service.getAllAssignments(
            mapOf("id" to "user-1", "deviceId" to "device-abc")
        )

        assertEquals(1, result.size)
        assertEquals(doc, result["id||user-1"])
    }

    @Test
    fun `getAllAssignments returns empty map for empty attributes input`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, localStorage = cache)

        val result = service.getAllAssignments(emptyMap())

        assertEquals(emptyMap(), result)
    }

    @Test
    fun `custom prefix is used when storing and retrieving`() = scope.runTest {
        val cache = MapCachingLayer()
        val service = GBStickyBucketServiceImp(scope, prefix = "myPrefix__", localStorage = cache)
        val doc = GBStickyAssignmentsDocument("id", "user-1", mapOf("exp" to "0"))

        service.saveAssignments(doc)

        assertEquals(true, cache.store.containsKey("myPrefix__id||user-1"))
        assertEquals(false, cache.store.containsKey("${prefix}id||user-1"))

        val result = service.getAssignments("id", "user-1")
        assertEquals(doc, result)
    }
}
