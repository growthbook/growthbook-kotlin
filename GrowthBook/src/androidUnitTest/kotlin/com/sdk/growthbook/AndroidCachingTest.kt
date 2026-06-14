package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingAndroid
import com.sdk.growthbook.sandbox.CachingImpl
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

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
}
