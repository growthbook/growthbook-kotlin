package com.sdk.growthbook

import com.sdk.growthbook.sandbox.CachingIOS
import com.sdk.growthbook.sandbox.CachingImpl
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class IOSCachingTest {

    @Test
    fun testActualLayer() {

        val cachingMgr = CachingImpl

        assertTrue(cachingMgr.getLayer() is CachingIOS)

    }

    @Test
    fun testCachingIOSFileName() {
        val manager = CachingIOS()

        val fileName = "gb-features.txt"

        val filePath = manager.getTargetFile(fileName)

        assertTrue(filePath.startsWith("/Users"))
        assertTrue(filePath.endsWith(fileName))
    }

    @Test
    fun testCachingIOS() {
        val manager = CachingIOS()

        val fileName = "gb-features.txt"

        manager.saveContent(fileName, JsonPrimitive("GrowthBook"))

        val fileContents = manager.getContent(fileName)

        assertTrue(fileContents != null)
        assertTrue(fileContents.jsonPrimitive.content == "GrowthBook")
    }



}