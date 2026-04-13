package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.utils.toJsonElement
import com.sdk.growthbook.utils.toList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionsTest {

    @Test
    fun `toHashMap - empty object returns empty map`() {
        val result = JsonObject(emptyMap()).toHashMap()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toHashMap - string value is preserved as String`() {
        val obj = buildJsonObject { put("key", JsonPrimitive("hello")) }
        val result = obj.toHashMap()
        assertEquals("hello", result["key"])
    }

    @Test
    fun `toHashMap - number value is preserved as String content`() {
        val obj = buildJsonObject { put("num", JsonPrimitive(42)) }
        val result = obj.toHashMap()
        assertEquals("42", result["num"])
    }

    @Test
    fun `toHashMap - boolean value is preserved as String content`() {
        val obj = buildJsonObject { put("flag", JsonPrimitive(true)) }
        val result = obj.toHashMap()
        assertEquals("true", result["flag"])
    }

    @Test
    fun `toHashMap - nested JsonObject becomes nested HashMap`() {
        val obj = buildJsonObject {
            put("outer", buildJsonObject {
                put("inner", JsonPrimitive("value"))
            })
        }
        val result = obj.toHashMap()
        val nested = result["outer"] as? HashMap<*, *>
        assertEquals("value", nested?.get("inner"))
    }

    @Test
    fun `toHashMap - JsonArray value becomes List`() {
        val obj = buildJsonObject {
            put("items", buildJsonArray {
                add(JsonPrimitive("a"))
                add(JsonPrimitive("b"))
            })
        }
        val result = obj.toHashMap()
        val list = result["items"] as? List<*>
        assertEquals(listOf("a", "b"), list)
    }

    @Test
    fun `toHashMap - multiple keys all converted`() {
        val obj = buildJsonObject {
            put("a", JsonPrimitive("1"))
            put("b", JsonPrimitive("2"))
            put("c", JsonPrimitive("3"))
        }
        val result = obj.toHashMap()
        assertEquals(3, result.size)
        assertEquals("1", result["a"])
        assertEquals("2", result["b"])
        assertEquals("3", result["c"])
    }

    @Test
    fun `toHashMap - deeply nested objects are recursively converted`() {
        val obj = buildJsonObject {
            put("l1", buildJsonObject {
                put("l2", buildJsonObject {
                    put("l3", JsonPrimitive("deep"))
                })
            })
        }
        val result = obj.toHashMap()
        val l1 = result["l1"] as HashMap<*, *>
        val l2 = l1["l2"] as HashMap<*, *>
        assertEquals("deep", l2["l3"])
    }

    @Test
    fun `toList - empty array returns empty list`() {
        val result = JsonArray(emptyList()).toList()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toList - string primitives become String elements`() {
        val array = buildJsonArray {
            add(JsonPrimitive("x"))
            add(JsonPrimitive("y"))
        }
        val result = array.toList()
        assertEquals(listOf("x", "y"), result)
    }

    @Test
    fun `toList - number primitives become String content`() {
        val array = buildJsonArray {
            add(JsonPrimitive(1))
            add(JsonPrimitive(2.5))
        }
        val result = array.toList()
        assertEquals("1", result[0])
        assertEquals("2.5", result[1])
    }

    @Test
    fun `toList - boolean primitives become String content`() {
        val array = buildJsonArray {
            add(JsonPrimitive(true))
            add(JsonPrimitive(false))
        }
        val result = array.toList()
        assertEquals("true", result[0])
        assertEquals("false", result[1])
    }

    @Test
    fun `toList - nested JsonObject becomes HashMap`() {
        val array = buildJsonArray {
            add(buildJsonObject { put("k", JsonPrimitive("v")) })
        }
        val result = array.toList()
        val map = result[0] as? HashMap<*, *>
        assertEquals("v", map?.get("k"))
    }

    @Test
    fun `toList - nested JsonArray falls through to primitive content`() {
        val inner = buildJsonArray { add(JsonPrimitive("item")) }
        val outer = buildJsonArray { add(inner) }
        val result = outer.toList()
        assertEquals(1, result.size)
    }

    @Test
    fun `toList - mixed element types in single array`() {
        val array = buildJsonArray {
            add(JsonPrimitive("text"))
            add(JsonPrimitive(99))
            add(buildJsonObject { put("x", JsonPrimitive("1")) })
        }
        val result = array.toList()
        assertEquals(3, result.size)
        assertEquals("text", result[0])
        assertEquals("99", result[1])
        assertTrue(result[2] is HashMap<*, *>)
    }

    @Test
    fun `List toJsonElement - empty list produces empty JsonArray`() {
        val result = emptyList<Any>().toJsonElement()
        assertTrue(result is JsonArray)
        assertEquals(0, result.jsonArray.size)
    }

    @Test
    fun `List toJsonElement - null elements are skipped`() {
        val list: List<*> = listOf("a", null, "b")
        val result = list.toJsonElement().jsonArray
        assertEquals(2, result.size)
        assertEquals("a", result[0].jsonPrimitive.content)
        assertEquals("b", result[1].jsonPrimitive.content)
    }

    @Test
    fun `List toJsonElement - Boolean elements encoded correctly`() {
        val result = listOf(true, false).toJsonElement().jsonArray
        assertEquals("true", result[0].jsonPrimitive.content)
        assertEquals("false", result[1].jsonPrimitive.content)
    }

    @Test
    fun `List toJsonElement - Number elements encoded correctly`() {
        val result = listOf(1, 3.14).toJsonElement().jsonArray
        assertEquals(1.0, result[0].jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(3.14, result[1].jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun `List toJsonElement - String elements encoded correctly`() {
        val result = listOf("hello", "world").toJsonElement().jsonArray
        assertEquals("hello", result[0].jsonPrimitive.content)
        assertEquals("world", result[1].jsonPrimitive.content)
    }

    @Test
    fun `List toJsonElement - nested Map becomes JsonObject`() {
        val result = listOf(mapOf("key" to "val")).toJsonElement().jsonArray
        val obj = result[0].jsonObject
        assertEquals("val", obj["key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `List toJsonElement - nested List becomes JsonArray`() {
        val result = listOf(listOf("inner")).toJsonElement().jsonArray
        val inner = result[0].jsonArray
        assertEquals("inner", inner[0].jsonPrimitive.content)
    }

    @Test
    fun `List toJsonElement - unknown type falls back to toString`() {
        data class Custom(val x: Int)

        val result = listOf(Custom(7)).toJsonElement().jsonArray
        assertEquals("Custom(x=7)", result[0].jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - empty map produces empty JsonObject`() {
        val result = emptyMap<String, Any>().toJsonElement()
        assertTrue(result is JsonObject)
        assertEquals(0, result.jsonObject.size)
    }

    @Test
    fun `Map toJsonElement - non-String key is skipped`() {
        @Suppress("UNCHECKED_CAST")
        val map: Map<*, *> = mapOf(1 to "one", "valid" to "ok")
        val result = map.toJsonElement().jsonObject
        assertNull(result["1"])
        assertEquals("ok", result["valid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - null value is skipped`() {
        @Suppress("UNCHECKED_CAST")
        val map: Map<*, *> = mapOf("present" to "yes", "absent" to null)
        val result = map.toJsonElement().jsonObject
        assertEquals("yes", result["present"]!!.jsonPrimitive.content)
        assertNull(result["absent"])
    }

    @Test
    fun `Map toJsonElement - Boolean value encoded correctly`() {
        val result = mapOf("a" to true, "b" to false).toJsonElement().jsonObject
        assertEquals("true", result["a"]!!.jsonPrimitive.content)
        assertEquals("false", result["b"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - Number value encoded correctly`() {
        val result = mapOf("n" to 42, "d" to 1.5).toJsonElement().jsonObject
        assertEquals(42.0, result["n"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(1.5, result["d"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun `Map toJsonElement - String value encoded correctly`() {
        val result = mapOf("key" to "value").toJsonElement().jsonObject
        assertEquals("value", result["key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - nested Map becomes nested JsonObject`() {
        val result = mapOf("outer" to mapOf("inner" to "deep")).toJsonElement().jsonObject
        val inner = result["outer"]!!.jsonObject
        assertEquals("deep", inner["inner"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - List value becomes JsonArray`() {
        val result = mapOf("list" to listOf("x", "y")).toJsonElement().jsonObject
        val array = result["list"]!!.jsonArray
        assertEquals(2, array.size)
        assertEquals("x", array[0].jsonPrimitive.content)
        assertEquals("y", array[1].jsonPrimitive.content)
    }

    @Test
    fun `Map toJsonElement - unknown type falls back to toString`() {
        data class Custom(val n: Int)

        val result = mapOf("obj" to Custom(5)).toJsonElement().jsonObject
        assertEquals("Custom(n=5)", result["obj"]!!.jsonPrimitive.content)
    }
}
