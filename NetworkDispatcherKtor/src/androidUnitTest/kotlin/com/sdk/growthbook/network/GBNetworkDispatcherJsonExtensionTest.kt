package com.sdk.growthbook.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Map<*,*>.toJsonElement() and List<*>.toJsonElement() extensions.
 * These are pure functions — no HTTP or coroutines needed.
 */
class GBNetworkDispatcherJsonExtensionTest {

    @Test
    fun `string value is encoded as JsonPrimitive`() {
        val result = mapOf("key" to "hello").toJsonElement().jsonObject

        assertEquals("hello", result["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `boolean true is encoded as JsonPrimitive`() {
        val result = mapOf("flag" to true).toJsonElement().jsonObject

        assertTrue(result["flag"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `boolean false is encoded as JsonPrimitive`() {
        val result = mapOf("flag" to false).toJsonElement().jsonObject

        assertEquals(false, result["flag"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `integer number is encoded as JsonPrimitive`() {
        val result = mapOf("count" to 42).toJsonElement().jsonObject

        assertEquals(42.0, result["count"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `double number is encoded as JsonPrimitive`() {
        val result = mapOf("ratio" to 3.14).toJsonElement().jsonObject

        assertEquals(3.14, result["ratio"]!!.jsonPrimitive.double, 0.001)
    }

    @Test
    fun `non-string key is skipped`() {
        @Suppress("UNCHECKED_CAST")
        val map: Map<*, *> = mapOf(42 to "value", "validKey" to "ok")
        val result = map.toJsonElement().jsonObject

        assertNull(result[42.toString()])  // numeric key skipped
        assertEquals("ok", result["validKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `null value in map is skipped`() {
        @Suppress("UNCHECKED_CAST")
        val map: Map<String, Any?> = mapOf("present" to "yes", "absent" to null)
        val result = (map as Map<*, *>).toJsonElement().jsonObject

        assertEquals("yes", result["present"]?.jsonPrimitive?.content)
        assertNull(result["absent"])
    }

    @Test
    fun `nested map is encoded as nested JsonObject`() {
        val result = mapOf(
            "outer" to mapOf("inner" to "value")
        ).toJsonElement().jsonObject

        val inner = result["outer"]?.jsonObject
        assertEquals("value", inner?.get("inner")?.jsonPrimitive?.content)
    }

    @Test
    fun `list value is encoded as JsonArray`() {
        val result = mapOf(
            "items" to listOf("a", "b", "c")
        ).toJsonElement().jsonObject

        val array = result["items"]?.jsonArray
        assertEquals(3, array?.size)
        assertEquals("a", array?.get(0)?.jsonPrimitive?.content)
        assertEquals("c", array?.get(2)?.jsonPrimitive?.content)
    }

    @Test
    fun `list of numbers is encoded correctly`() {
        val result = mapOf(
            "nums" to listOf(1, 2, 3)
        ).toJsonElement().jsonObject

        val array = result["nums"]!!.jsonArray
        assertEquals(1.0, array[0].jsonPrimitive.double, 0.0)
        assertEquals(3.0, array[2].jsonPrimitive.double, 0.0)
    }

    @Test
    fun `list of booleans is encoded correctly`() {
        val result = mapOf(
            "flags" to listOf(true, false, true)
        ).toJsonElement().jsonObject

        val array = result["flags"]?.jsonArray
        assertEquals(true, array?.get(0)?.jsonPrimitive?.boolean)
        assertEquals(false, array?.get(1)?.jsonPrimitive?.boolean)
    }

    @Test
    fun `list of maps produces array of objects`() {
        val result = mapOf(
            "records" to listOf(
                mapOf("id" to 1),
                mapOf("id" to 2)
            )
        ).toJsonElement().jsonObject

        val array = result["records"]!!.jsonArray
        assertEquals(2, array.size)
        assertEquals(1.0, array[0].jsonObject["id"]!!.jsonPrimitive.double, 0.0)
        assertEquals(2.0, array[1].jsonObject["id"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `nested list inside list is encoded as nested JsonArray`() {
        val result = mapOf(
            "matrix" to listOf(listOf(1, 2), listOf(3, 4))
        ).toJsonElement().jsonObject

        val inner = result["matrix"]!!.jsonArray[0].jsonArray
        assertEquals(1.0, inner[0].jsonPrimitive.double, 0.0)
    }

    @Test
    fun `null values in list are skipped`() {
        @Suppress("UNCHECKED_CAST")
        val list: List<*> = listOf("a", null, "b")
        val result = list.toJsonElement() as JsonArray

        assertEquals(2, result.size)
        assertEquals("a", result[0].jsonPrimitive.content)
        assertEquals("b", result[1].jsonPrimitive.content)
    }

    @Test
    fun `empty map produces empty JsonObject`() {
        val result = emptyMap<String, Any>().toJsonElement()

        assertTrue(result is JsonObject)
        assertEquals(0, result.jsonObject.size)
    }

    @Test
    fun `empty list produces empty JsonArray`() {
        val result = emptyList<Any>().toJsonElement()

        assertTrue(result is JsonArray)
        assertEquals(0, result.jsonArray.size)
    }

    @Test
    fun `unknown type falls back to toString encoding`() {
        data class Custom(val x: Int)

        val result = mapOf("obj" to Custom(99)).toJsonElement().jsonObject

        assertEquals("Custom(x=99)", result["obj"]?.jsonPrimitive?.content)
    }
}
