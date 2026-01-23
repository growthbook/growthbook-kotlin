package com.sdk.growthbook.network

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized

/**
 * Thread-safe LRU (Least Recently Used) cache for storing ETags.
 *
 * This cache has a maximum capacity and automatically evicts the least recently
 * accessed entries when the capacity is exceeded. All operations are thread-safe.
 *
 * @param maxSize Maximum number of entries to store (default: 100)
 */
@OptIn(InternalCoroutinesApi::class)
class LruETagCache(private val maxSize: Int = 100) : SynchronizedObject() {

    private val cache = LinkedHashMap<String, String>()

    /**
     * Retrieves the ETag for the given URL.
     *
     * @param url The URL key
     * @return The ETag value, or null if not present
     */
    fun get(url: String): String? = synchronized(this) {
        val value = cache.remove(url)
        if (value != null) {
            cache[url] = value
        }
        return value
    }

    /**
     * Stores an ETag for the given URL.
     *
     * @param url The URL key
     * @param eTag The ETag value to store
     */
    fun put(url: String, eTag: String?) = synchronized(this) {
        if (eTag != null) {
            cache.remove(url)
            cache[url] = eTag

            if (cache.size > maxSize) {
                val oldestKey = cache.keys.firstOrNull()
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                }
            }
        } else {
            cache.remove(url)
        }
        Unit
    }

    /**
     * Removes the ETag for the given URL.
     *
     * @param url The URL key
     * @return The removed ETag value, or null if not present
     */
    fun remove(url: String): String? = synchronized(this) {
        return cache.remove(url)
    }

    /**
     * Clears all entries from the cache.
     */
    fun clear() = synchronized(this) {
        cache.clear()
    }

    /**
     * Returns the current number of entries in the cache.
     */
    fun size(): Int = synchronized(this) {
        return cache.size
    }

    /**
     * Returns true if the cache contains an entry for the given URL.
     */
    fun contains(url: String): Boolean = synchronized(this) {
        return cache.containsKey(url)
    }
}

