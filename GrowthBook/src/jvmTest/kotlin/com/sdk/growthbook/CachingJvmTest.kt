package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingJvm
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CyclicBarrier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CachingJvmTest {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun testActualLayer() {
        val cachingMgr = CachingImpl
        assertTrue(cachingMgr.getLayer() is CachingJvm)
    }

    @Test
    fun testTargetFileName() {
        val manager = CachingJvm()

        val fileName = "gb-features.txt"

        val file = manager.getTargetFile(fileName)

        assertEquals(file.name, "gb-features.txt")
        assertEquals(file.parentFile.name, "GrowthBook-KMM")
    }

    @Test
    fun testSaveAndGetRoundTrip() {
        val manager = CachingJvm()

        val fileName = "gb-features.txt"

        manager.saveContent(fileName, JsonPrimitive("GrowthBook"))

        val fileContents = manager.getContent(fileName)

        assertTrue(fileContents != null)
        assertEquals(fileContents.jsonPrimitive.content, "GrowthBook")
    }

    @Test
    fun testMissingFileReturnsNull() {
        assertNull(CachingJvm().getContent("does-not-exist"))
    }

    @Test
    fun testCorruptCacheIsDeletedAndReturnsNull() {
        val manager = CachingJvm()
        val fileName = "gb-features.txt"
        val file = manager.getTargetFile(fileName)
        file.writeText("{ not valid json")

        assertNull(manager.getContent(fileName), "corrupt cache must return null")
        assertTrue(!file.exists(), "corrupt cache file must be deleted")
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
    fun testRenameFailureThrowsAndPreservesExistingCache() {
        val manager = CachingJvm()
        val fileName = "gb-features.txt"
        val gbDir = File(CachingJvm.baseDir, "GrowthBook-KMM").also { it.mkdirs() }

        // Force renameTo(target) to fail: make the target path a non-empty directory.
        val target = File(gbDir, fileName).also { it.mkdirs() }
        File(target, "blocker").writeText("x")

        assertFailsWith<IOException> {
            manager.saveContent(fileName, JsonPrimitive("GrowthBook"))
        }
        assertTrue(
            gbDir.listFiles()?.none { it.name.endsWith(".tmp") } ?: true,
            "temp file must be deleted when the rename fails",
        )
        assertTrue(target.isDirectory, "a failed rename must not destroy the existing cache entry")
    }

    @Test
    fun testConcurrentWritersDoNotCorruptCache() {
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
