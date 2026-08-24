package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingJS
import kotlinx.browser.localStorage
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachingJSTest {
    private val fileName = "gb-features-test"

    @BeforeTest
    fun setUp() {
        localStorage.removeItem(storageKey(fileName))
    }

    @AfterTest
    fun tearDown() {
        localStorage.removeItem(storageKey(fileName))
    }

    @Test
    fun testActualLayerIsCachingJS() {
        assertTrue(CachingImpl.getLayer() is CachingJS)
    }

    @Test
    fun testSaveAndRoundTrip() {
        val manager = CachingJS()
        manager.saveContent(fileName, JsonPrimitive("valid"))
        val content = manager.getContent(fileName)
        assertNotNull(content)
        assertEquals(JsonPrimitive("valid"), content)
    }

    @Test
    fun testMissingKeyReturnsNull() {
        assertNull(CachingJS().getContent("does-not-exist-key"))
    }

    @Test
    fun testTxtSuffixNormalized() {
        val manager = CachingJS()
        manager.saveContent("$fileName.txt", JsonPrimitive("valid"))
        val content = manager.getContent(fileName)
        assertNotNull(content)
        assertEquals(JsonPrimitive("valid"), content)
    }

    @Test
    fun testCorruptCacheIsDeletedAndReturnsNull() {
        val manager = CachingJS()
        manager.saveContent(fileName, JsonPrimitive("valid"))
        localStorage.setItem(storageKey(fileName), "{")
        assertNull(manager.getContent(fileName))
        assertNull(localStorage.getItem(storageKey(fileName)))
    }

    /**
     * Mirror of CachingJS.storageKey, for inspecting raw write
     */
    private fun storageKey(fileName: String): String {
        val base = if (fileName.endsWith(".txt")) fileName.removeSuffix(".txt") else fileName
        return "GrowthBook-KMM/$base"
    }
}
