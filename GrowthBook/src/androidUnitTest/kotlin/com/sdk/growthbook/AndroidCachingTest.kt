package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingAndroid
import com.sdk.growthbook.sandbox.CachingImpl
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
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
        assertTrue(cachingMgr.getLayer("") is CachingAndroid)
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
}
