package com.sdk.growthbook.network

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe LRU (Least Recently Used) cache for storing ETags.
 * 
 * This cache has a maximum capacity and automatically evicts the least recently
 * accessed entries when the capacity is exceeded. All operations are thread-safe.
 * 
 * @param maxSize Maximum number of entries to store (default: 100)
 */
internal class LruETagCache(private val maxSize: Int = 100) {
    
    // LinkedHashMap with accessOrder=true maintains access order (LRU)
    private val cache = object : LinkedHashMap<String, String>(
        maxSize + 1,  // Initial capacity
        0.75f,        // Load factor
        true          // Access order (true = LRU, false = insertion order)
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>): Boolean {
            return size > maxSize
        }
    }
    
    // ReadWrite lock for better performance (multiple readers, single writer)
    private val lock = ReentrantReadWriteLock()
    
    /**
     * Retrieves the ETag for the given URL.
     * 
     * @param url The URL key
     * @return The ETag value, or null if not present
     */
    fun get(url: String): String? = lock.read {
        cache[url]
    }
    
    /**
     * Stores an ETag for the given URL.
     * 
     * @param url The URL key
     * @param eTag The ETag value to store
     */
    fun put(url: String, eTag: String?) {
        lock.write {
            if (eTag != null) {
                cache[url] = eTag
            } else {
                cache.remove(url)
            }
        }
    }
    
    /**
     * Removes the ETag for the given URL.
     * 
     * @param url The URL key
     * @return The removed ETag value, or null if not present
     */
    fun remove(url: String): String? = lock.write {
        cache.remove(url)
    }
    
    /**
     * Clears all entries from the cache.
     */
    fun clear() = lock.write {
        cache.clear()
    }
    
    /**
     * Returns the current number of entries in the cache.
     */
    fun size(): Int = lock.read {
        cache.size
    }
    
    /**
     * Returns true if the cache contains an entry for the given URL.
     */
    fun contains(url: String): Boolean = lock.read {
        cache.containsKey(url)
    }
}

