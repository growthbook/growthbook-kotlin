package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingAndroid
import com.sdk.growthbook.sandbox.CachingImpl
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CyclicBarrier
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AndroidCachingTest {

    @Rule @JvmField
    var mTempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingAndroid.filesDir = mTempFolder.newFolder()
    }

    @Test
    fun testActualLayer() {
        val cachingMgr = CachingImpl
        assertTrue(cachingMgr.getLayer() is CachingAndroid)
    }

    @Test
    fun testCachingAndroidFileName() {
        val manager = CachingAndroid()

        val fileName = "gb-features.txt"

        val file = manager.getTargetFile(fileName)

        assertTrue(file != null)
    }

    @Test
    fun testCachingAndroid() {
        val manager = CachingAndroid()

        val fileName = "gb-features.txt"

        manager.saveContent(fileName, JsonPrimitive("GrowthBook"))

        val fileContents = manager.getContent(fileName)

        assertTrue(fileContents != null)
        assertTrue(fileContents.jsonPrimitive.content == "GrowthBook")
    }

    @Test
    fun testLegacyCacheMigratedToScopedFeatureCache() {
        val manager = CachingAndroid()
        val gbDir = File(CachingAndroid.filesDir, "GrowthBook-KMM").also { it.mkdirs() }
        File(gbDir, "FeatureCache.txt").writeText("\"cached-features\"")

        val result = manager.getContent("FeatureCache_myApiKey")

        assertTrue(result != null)
        assertTrue(result.jsonPrimitive.content == "cached-features")
        assertTrue(!File(gbDir, "FeatureCache.txt").exists())
    }

    @Test
    fun testLegacyCacheNotMigratedToStickyBucketFile() {
        val manager = CachingAndroid()
        val gbDir = File(CachingAndroid.filesDir, "GrowthBook-KMM").also { it.mkdirs() }
        val legacyFile = File(gbDir, "FeatureCache.txt")
        legacyFile.writeText("\"cached-features\"")

        val stickyBucketResult = manager.getContent("gbStickyBuckets__id||user123")

        assertNull(stickyBucketResult)
        assertTrue(legacyFile.exists(), "Legacy FeatureCache.txt must not be consumed by a sticky bucket read")
    }

    @Test
    fun testRenameFailureThrowsAndPreservesExistingCache() {
        val manager = CachingAndroid()
        val gbDir = File(CachingAndroid.filesDir, "GrowthBook-KMM").also { it.mkdirs() }

        // Force renameTo(target) to return false by making the target path a
        // non-empty directory (rename-over-non-empty-dir fails at the OS level).
        val target = File(gbDir, "gb-features.txt").also { it.mkdirs() }
        File(target, "blocker").writeText("x")

        assertFailsWith<IOException> {
            manager.saveContent("gb-features.txt", JsonPrimitive("GrowthBook"))
        }

        assertTrue(
            gbDir.listFiles()?.none { it.name.endsWith(".tmp") } ?: true,
            "temp file must be deleted when the rename fails",
        )
        assertTrue(
            target.isDirectory,
            "a failed rename must not silently destroy the existing cache entry",
        )
    }

    @Test
    fun testGetLayerReturnsSharedInstance() {
        assertSame(
            CachingImpl.getLayer(),
            CachingImpl.getLayer(),
            "getLayer() must return a shared instance so the per-file lock serializes all writers",
        )
    }

    @Test
    fun testConcurrentWritersFromSeparateLayersDoNotCorruptCache() {
        val fileName = "gb-features.txt"
        val threadCount = 8
        val iterations = 50
        // Distinct, large payloads so any interleaving of writes produces invalid JSON.
        val payloads = (0 until threadCount).map { t ->
            JsonPrimitive("payload-$t-" + "x".repeat(2000))
        }

        val barrier = CyclicBarrier(threadCount)
        val workers = (0 until threadCount).map { t ->
            Thread {
                barrier.await()
                repeat(iterations) {
                    // Each "SDK instance" obtains its layer the way production does.
                    CachingImpl.getLayer().saveContent(fileName, payloads[t])
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        val result = CachingImpl.getLayer().getContent(fileName)
        assertTrue(result != null, "cache must be readable after concurrent writes")
        assertTrue(
            payloads.any { it == result },
            "cache must hold exactly one writer's complete payload, not interleaved content",
        )
    }
}
