package com.sdk.growthbook.tests

import com.sdk.growthbook.features.CachePolicy
import com.sdk.growthbook.features.CacheZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachePolicyTest {

    @Test
    fun testThreeTierZones() {
        val policy = CachePolicy(staleTtl = 1000, cacheMaxAge = 5000)
        // FRESH: age < staleTtl
        assertEquals(CacheZone.FRESH, policy.classify(0))
        assertEquals(CacheZone.FRESH, policy.classify(999))
        // STALE: staleTtl <= age < cacheMaxAge (boundary age == staleTtl is NOT fresh)
        assertEquals(CacheZone.STALE, policy.classify(1000))
        assertEquals(CacheZone.STALE, policy.classify(4999))
        // EXPIRED: age >= cacheMaxAge (boundary age == cacheMaxAge is expired)
        assertEquals(CacheZone.EXPIRED, policy.classify(5000))
        assertEquals(CacheZone.EXPIRED, policy.classify(9999))
    }

    @Test
    fun testCacheMaxAgeAloneHasNoExpiredZone() {
        // Backward-compatible 7.3.0 behaviour: without staleTtl the hard ceiling is disarmed, so a
        // cache beyond cacheMaxAge is STALE (served + revalidated), never EXPIRED.
        val policy = CachePolicy(staleTtl = null, cacheMaxAge = 5000)
        assertEquals(CacheZone.FRESH, policy.classify(4999))
        assertEquals(CacheZone.STALE, policy.classify(5000))
        assertEquals(CacheZone.STALE, policy.classify(Long.MAX_VALUE))
    }

    @Test
    fun testStaleTtlAloneHasInnerWindowButNoCeiling() {
        val policy = CachePolicy(staleTtl = 1000, cacheMaxAge = null)
        assertEquals(CacheZone.FRESH, policy.classify(999))
        assertEquals(CacheZone.STALE, policy.classify(1000))
        assertEquals(CacheZone.STALE, policy.classify(Long.MAX_VALUE)) // no ceiling -> never EXPIRED
    }

    @Test
    fun testBothUnsetIsAlwaysStale() {
        val policy = CachePolicy(staleTtl = null, cacheMaxAge = null)
        assertEquals(CacheZone.STALE, policy.classify(0))
        assertEquals(CacheZone.STALE, policy.classify(Long.MAX_VALUE))
    }

    @Test
    fun testStaleTtlNotSmallerThanCacheMaxAgeThrows() {
        assertFailsWith<IllegalArgumentException> { CachePolicy(staleTtl = 5000, cacheMaxAge = 5000) }
        assertFailsWith<IllegalArgumentException> { CachePolicy(staleTtl = 6000, cacheMaxAge = 5000) }
    }

    @Test
    fun testServeStaleOnErrorExposed() {
        assertTrue(CachePolicy(null, null, serveStaleOnError = true).serveStaleOnError)
        assertFalse(CachePolicy(null, null).serveStaleOnError)
    }
}
