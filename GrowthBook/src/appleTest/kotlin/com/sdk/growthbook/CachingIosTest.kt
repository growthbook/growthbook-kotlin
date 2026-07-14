package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingIOS
import com.sdk.growthbook.sandbox.CachingImpl
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachingIosTest {
    private val fileName = "gb-features-test"

    @OptIn(ExperimentalForeignApi::class)
    @AfterTest
    fun tearDown() {
        val path = targetPath(fileName) ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun testActualLayerIsCachingIOS() {
        assertTrue(CachingImpl.getLayer() is CachingIOS)
    }

    @Test
    fun testSaveAndGetRoundTrip() {
        val manager = CachingIOS()
        manager.saveContent(fileName, JsonPrimitive("GrowthBook"))
        val contents = manager.getContent(fileName)
        assertTrue(contents != null)
        assertEquals("GrowthBook", contents.jsonPrimitive.content)
    }

    @Test
    fun testMissingFileReturnsNull() {
        assertNull(CachingIOS().getContent("does-not-exist-file"))
    }

    @Test
    fun testTxtSuffixNormalized() {
        val manager = CachingIOS()
        manager.saveContent("$fileName.txt", JsonPrimitive("suffixed"))
        // reading WITHOUT the suffix must resolve to the same file
        val contents = manager.getContent(fileName)
        assertTrue(contents != null)
        assertEquals("suffixed", contents.jsonPrimitive.content)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testCorruptCacheIsDeletedAndReturnsNull() {
        val manager = CachingIOS()
        manager.saveContent(fileName, JsonPrimitive("valid"))
        val path = targetPath(fileName)!!
        // overwrite with invalid JSON
        ("{" as NSString).writeToFile(
            path, atomically = true, encoding = NSUTF8StringEncoding, error = null,
        )
        assertNull(manager.getContent(fileName), "corrupt cache must return null")
        assertTrue(
            !NSFileManager.defaultManager.fileExistsAtPath(path),
            "corrupt cache file must be deleted",
        )
    }

    private fun targetPath(name: String): String? {
        val caches = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true,
        ).firstOrNull() as? String ?: return null
        val dir = (caches as NSString).stringByAppendingPathComponent("GrowthBook-KMM")
        val base = if (name.endsWith(".txt", true)) name.removeSuffix(".txt") else name
        return (dir as NSString).stringByAppendingPathComponent("$base.txt")
    }
}
