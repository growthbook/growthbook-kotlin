package com.sdk.growthbook.network

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LruETagCacheTest {
    
    private lateinit var cache: LruETagCache
    
    @Before
    fun setup() {
        cache = LruETagCache(maxSize = 3)
    }
    
    @Test
    fun `test basic put and get operations`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        
        assertEquals("etag1", cache.get("url1"))
        assertEquals("etag2", cache.get("url2"))
        assertNull(cache.get("url3"))
    }
    
    @Test
    fun `test LRU eviction when capacity exceeded`() {
        // Add 3 items (fills capacity)
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        cache.put("url3", "etag3")
        
        // Add 4th item - should evict url1 (least recently used)
        cache.put("url4", "etag4")
        
        assertNull(cache.get("url1"))  // Evicted
        assertEquals("etag2", cache.get("url2"))
        assertEquals("etag3", cache.get("url3"))
        assertEquals("etag4", cache.get("url4"))
        assertEquals(3, cache.size())
    }
    
    @Test
    fun `test accessing an entry updates its position in LRU`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        cache.put("url3", "etag3")
        
        // Access url1 to make it most recently used
        cache.get("url1")
        
        // Add url4 - should evict url2 (now least recently used)
        cache.put("url4", "etag4")
        
        assertEquals("etag1", cache.get("url1"))  // Still present
        assertNull(cache.get("url2"))  // Evicted
        assertEquals("etag3", cache.get("url3"))
        assertEquals("etag4", cache.get("url4"))
    }
    
    @Test
    fun `test null value removes entry`() {
        cache.put("url1", "etag1")
        assertEquals("etag1", cache.get("url1"))
        
        cache.put("url1", null)
        assertNull(cache.get("url1"))
        assertEquals(0, cache.size())
    }
    
    @Test
    fun `test remove operation`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        
        val removed = cache.remove("url1")
        
        assertEquals("etag1", removed)
        assertNull(cache.get("url1"))
        assertEquals(1, cache.size())
    }
    
    @Test
    fun `test clear operation`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        cache.put("url3", "etag3")
        
        cache.clear()
        
        assertEquals(0, cache.size())
        assertNull(cache.get("url1"))
        assertNull(cache.get("url2"))
        assertNull(cache.get("url3"))
    }
    
    @Test
    fun `test contains operation`() {
        cache.put("url1", "etag1")
        
        assertTrue(cache.contains("url1"))
        assertFalse(cache.contains("url2"))
    }
    
    @Test
    fun `test concurrent reads and writes`() {
        val largeCache = LruETagCache(maxSize = 100)
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)
        
        // Launch 100 concurrent operations
        repeat(100) { i ->
            executor.submit {
                try {
                    when (i % 3) {
                        0 -> largeCache.put("url$i", "etag$i")
                        1 -> largeCache.get("url${i - 1}")
                        2 -> largeCache.contains("url${i - 2}")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        
        // Wait for all operations to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        
        // Verify cache is still operational and size is within bounds
        assertTrue(largeCache.size() <= 100)
    }
    
    @Test
    fun `test thread safety with rapid concurrent access`() {
        val threadSafeCache = LruETagCache(maxSize = 10)
        val errors = mutableListOf<Throwable>()
        val threads = mutableListOf<Thread>()
        
        // Create 20 threads that rapidly access the cache
        repeat(20) { threadId ->
            val t = thread {
                try {
                    repeat(100) { i ->
                        val key = "url${i % 5}"
                        threadSafeCache.put(key, "etag$threadId-$i")
                        threadSafeCache.get(key)
                        threadSafeCache.contains(key)
                    }
                } catch (e: Throwable) {
                    synchronized(errors) {
                        errors.add(e)
                    }
                }
            }
            threads.add(t)
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join(5000) }
        
        // Verify no exceptions occurred
        if (errors.isNotEmpty()) {
            fail("Thread safety test failed with ${errors.size} errors: ${errors.first().message}")
        }
        
        // Verify cache is still within size bounds
        assertTrue(threadSafeCache.size() <= 10)
    }
    
    @Test
    fun `test large cache operations`() {
        val largeCache = LruETagCache(maxSize = 100)
        
        // Add 150 items
        repeat(150) { i ->
            largeCache.put("url$i", "etag$i")
        }
        
        // Should only have 100 items (the most recent ones)
        assertEquals(100, largeCache.size())
        
        // First 50 should be evicted
        repeat(50) { i ->
            assertNull(largeCache.get("url$i"))
        }
        
        // Last 100 should be present
        repeat(100) { i ->
            assertEquals("etag${i + 50}", largeCache.get("url${i + 50}"))
        }
    }
    
    @Test
    fun `test updating existing entry doesn't grow cache`() {
        cache.put("url1", "etag1")
        cache.put("url2", "etag2")
        assertEquals(2, cache.size())
        
        // Update existing entry
        cache.put("url1", "etag1-updated")
        
        // Size should still be 2
        assertEquals(2, cache.size())
        assertEquals("etag1-updated", cache.get("url1"))
    }
}

