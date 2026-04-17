package com.sdk.growthbook.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class OkHttpLruETagCacheTest {

    private lateinit var cache: OkHttpLruETagCache

    @Before
    fun setUp() {
        cache = OkHttpLruETagCache(maxSize = 3)
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("url1"))
    }

    @Test
    fun `put and get round-trips the ETag`() {
        cache.put("url1", "etag1")
        assertEquals("etag1", cache.get("url1"))
    }

    @Test
    fun `put with null removes existing entry`() {
        cache.put("url1", "etag1")
        cache.put("url1", null)
        assertNull(cache.get("url1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `put with null on absent key is a no-op`() {
        cache.put("url1", null)
        assertEquals(0, cache.size())
    }

    @Test
    fun `put overwrites existing ETag`() {
        cache.put("url1", "v1")
        cache.put("url1", "v2")
        assertEquals("v2", cache.get("url1"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `multiple different keys stored independently`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        assertEquals("etag1", cache.get("url1"))
        assertEquals("etag2", cache.get("url2"))
    }

    @Test
    fun `evicts least recently used when capacity exceeded`() {
        cache.put("url1", "e1")
        cache.put("url2", "e2")
        cache.put("url3", "e3")

        cache.put("url4", "e4")

        assertNull(cache.get("url1"))
        assertEquals("e2", cache.get("url2"))
        assertEquals("e3", cache.get("url3"))
        assertEquals("e4", cache.get("url4"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `accessing an entry promotes it so it is not evicted`() {
        cache.put("url1", "e1")
        cache.put("url2", "e2")
        cache.put("url3", "e3")

        cache.get("url1")

        cache.put("url4", "e4")

        assertEquals("e1", cache.get("url1"))
        assertNull(cache.get("url2"))
        assertEquals("e3", cache.get("url3"))
        assertEquals("e4", cache.get("url4"))
    }

    @Test
    fun `updating existing entry does not grow cache beyond maxSize`() {
        cache.put("url1", "e1")
        cache.put("url2", "e2")
        assertEquals(2, cache.size())

        cache.put("url1", "e1-updated")

        assertEquals(2, cache.size())
        assertEquals("e1-updated", cache.get("url1"))
    }

    @Test
    fun `remove returns the ETag and deletes it`() {
        cache.put("url1", "etag1")

        val removed = cache.remove("url1")

        assertEquals("etag1", removed)
        assertNull(cache.get("url1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `remove on absent key returns null`() {
        assertNull(cache.remove("missing"))
    }

    @Test
    fun `remove leaves other entries intact`() {
        cache.put("url1", "e1")
        cache.put("url2", "e2")

        cache.remove("url1")

        assertEquals("e2", cache.get("url2"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `clear removes all entries`() {
        cache.put("url1", "e1")
        cache.put("url2", "e2")
        cache.put("url3", "e3")

        cache.clear()

        assertEquals(0, cache.size())
        assertNull(cache.get("url1"))
        assertNull(cache.get("url2"))
        assertNull(cache.get("url3"))
    }

    @Test
    fun `clear on empty cache is a no-op`() {
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `size reflects number of stored entries`() {
        assertEquals(0, cache.size())
        cache.put("url1", "e1")
        assertEquals(1, cache.size())
        cache.put("url2", "e2")
        assertEquals(2, cache.size())
    }

    @Test
    fun `contains returns true for existing key`() {
        cache.put("url1", "e1")
        assertTrue(cache.contains("url1"))
    }

    @Test
    fun `contains returns false for missing key`() {
        assertFalse(cache.contains("missing"))
    }

    @Test
    fun `contains returns false after remove`() {
        cache.put("url1", "e1")
        cache.remove("url1")
        assertFalse(cache.contains("url1"))
    }

    @Test
    fun `contains returns false after clear`() {
        cache.put("url1", "e1")
        cache.clear()
        assertFalse(cache.contains("url1"))
    }

    @Test
    fun `large cache evicts only excess entries`() {
        val large = OkHttpLruETagCache(maxSize = 100)

        repeat(150) { i -> large.put("url$i", "etag$i") }

        assertEquals(100, large.size())
        repeat(50) { i -> assertNull(large.get("url$i")) }
        repeat(100) { i -> assertEquals("etag${i + 50}", large.get("url${i + 50}")) }
    }

    @Test
    fun `concurrent reads and writes do not corrupt cache`() {
        val large = OkHttpLruETagCache(maxSize = 100)
        val latch = CountDownLatch(100)
        val executor = Executors.newFixedThreadPool(10)

        repeat(100) { i ->
            executor.submit {
                try {
                    when (i % 3) {
                        0 -> large.put("url$i", "etag$i")
                        1 -> large.get("url${i - 1}")
                        2 -> large.contains("url${i - 2}")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Timed out", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(large.size() <= 100)
    }

    @Test
    fun `rapid concurrent put and get from multiple threads`() {
        val errors = mutableListOf<Throwable>()
        val threads = (1..20).map { threadId ->
            thread {
                try {
                    repeat(100) { i ->
                        val key = "url${i % 5}"
                        cache.put(key, "etag$threadId-$i")
                        cache.get(key)
                        cache.contains(key)
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                }
            }
        }

        threads.forEach { it.join(5_000) }

        if (errors.isNotEmpty()) {
            throw AssertionError("${errors.size} thread-safety errors: ${errors.first().message}")
        }
        assertTrue(cache.size() <= 3)
    }
}
