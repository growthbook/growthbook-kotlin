package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingWasmJS
import kotlinx.browser.localStorage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachingWasmJSTest {
    private val fileName = "gb-features-test"

    // localStorage is a shared global, so clean our namespaced key before and after each test.
    @BeforeTest
    fun setUp() {
        localStorage.removeItem(storageKey(fileName))
    }

    @AfterTest
    fun tearDown() {
        localStorage.removeItem(storageKey(fileName))
    }

    @Test
    fun testActualLayerIsCachingWasmJS() {
        assertTrue(CachingImpl.getLayer() is CachingWasmJS)
    }

    @Test
    fun testSaveAndRoundTrip() {
        val manager = CachingWasmJS()
        manager.saveContent(fileName, JsonPrimitive("valid"))
        val content = manager.getContent(fileName)
        assertNotNull(content)
        assertEquals("valid", content.jsonPrimitive.content)
    }

    @Test
    fun testMissingKeyReturnsNull() {
        assertNull(CachingWasmJS().getContent("does-not-exist-key"))
    }

    @Test
    fun testTxtSuffixNormalized() {
        val manager = CachingWasmJS()
        manager.saveContent("$fileName.txt", JsonPrimitive("suffixed"))
        val content = manager.getContent(fileName)
        assertNotNull(content)
        assertEquals("suffixed", content.jsonPrimitive.content)
    }

    @Test
    fun testCorruptCacheIsDeletedAndReturnsNull() {
        val manager = CachingWasmJS()
        manager.saveContent(fileName, JsonPrimitive("valid"))
        localStorage.setItem(storageKey(fileName), "{")
        assertNull(manager.getContent(fileName))
        assertNull(localStorage.getItem(storageKey(fileName)))
    }

    private fun storageKey(name: String): String {
        val base = if (name.endsWith(".txt", true)) name.removeSuffix(".txt") else name
        return "GrowthBook-KMM/$base"
    }
}
