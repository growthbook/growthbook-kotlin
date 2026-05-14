package com.sdk.growthbook.kotlinx.serialization

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GBValueTest {

    @Test
    fun `decodeAs converts GBJson to data class`() {
        val gbJson = GBJson(
            mapOf(
                "price" to GBNumber(99),
                "label" to GBString("Pro")
            )
        )

        val result = gbJson.decodeAs<TestConfig>()
        assertEquals(TestConfig(price = 99, label = "Pro"), result)
    }

    @Test
    fun `decodeAs converts GBBoolean to Boolean`() {
        val result = GBBoolean(true).decodeAs<Boolean>()
        assertEquals(true, result)
    }

    @Test
    fun `decodeAs converts GBString to String`() {
        val result = GBString("GrowthBook").decodeAs<String>()
        assertEquals("GrowthBook", result)
    }

    @Test
    fun `decodeAs converts GBNumber to Int`() {
        val result = GBNumber(99).decodeAs<Int>()
        assertEquals(99, result)
    }

    @Test
    fun `decodeAs converts GBArray to List`() {
        val gbArray = GBArray(listOf(GBNumber(99)))
        val result = gbArray.decodeAs<List<Int>>()
        assertEquals(listOf(99), result)
    }

    @Test
    fun `decodeAs returns null for invalid type conversion`() {
        val gbArray = GBArray(listOf(GBString("-")))
        val result = gbArray.decodeAs<List<Int>>()
        assertNull(result)
    }

    @Test
    fun `decodeAs returns null for GBNull`() {
        val result = GBNull.decodeAs<String>()
        assertNull(result)
    }

    @Test
    fun `decodeAs returns null for Unknown`() {
        val result = GBValue.Unknown.decodeAs<String>()
        assertNull(result)
    }

    @Test
    fun `decodeAs returns double for GBNumber`() {
        val result = GBNumber(3.14).decodeAs<Double>()
        assertEquals(3.14, result)
    }

    @Test
    fun `decodeAs returns data class with nested`() {
        val gbJson = GBJson(
            mapOf(
                "user" to GBJson(
                    mapOf(
                        "label" to GBString("Pro"),
                        "price" to GBNumber(30)
                    )
                ),
                "total" to GBNumber(100)
            )
        )

        val result = gbJson.decodeAs<OrderTest>()
        assertEquals(
            expected = OrderTest(
                user = TestConfig(
                    price = 30,
                    label = "Pro"
                ),
                total = 100
            ),
            actual = result
        )
    }

    @Test
    fun `gbSerialize converts GBNull to JsonNull`() {
        val result = GBNull.gbSerialize()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `gbSerialize converts GBBoolean to JsonPrimitive`() {
        val result = GBBoolean(true).gbSerialize()
        assertEquals(JsonPrimitive(true), result)
    }

    @Test
    fun `gbSerialize converts GBString to JsonPrimitive`() {
        val result = GBString("hello").gbSerialize()
        assertEquals(JsonPrimitive("hello"), result)
    }

    @Test
    fun `gbSerialize converts GBNumber to JsonPrimitive`() {
        val result = GBNumber(1).gbSerialize()
        assertEquals(JsonPrimitive(1), result)
    }

    @Test
    fun `gbSerialize converts GBArray to JsonArray`() {
        val result = GBArray(listOf(GBNumber(1))).gbSerialize()
        assertEquals(JsonArray(listOf(JsonPrimitive(1))), result)
    }

    @Test
    fun `gbSerialize converts GBJson to JsonObject`() {
        val result = GBJson(mapOf("id" to GBNumber(1))).gbSerialize()
        assertEquals(JsonObject(mapOf("id" to JsonPrimitive(1))), result)
    }

    @Test
    fun `gbSerialize converts Unknown to JsonNull`() {
        val result = GBValue.Unknown.gbSerialize()
        assertEquals(JsonNull, result)
    }

    @Test
    fun `from converts JsonNull to GBNull`() {
        val result = GBValue.from(JsonNull)
        assertEquals(GBNull, result)
    }

    @Test
    fun `from converts JsonPrimitive boolean to GBBoolean`() {
        val result = GBValue.from(JsonPrimitive(true))
        assertEquals(GBBoolean(true), result)
    }

    @Test
    fun `from converts JsonPrimitive string to GBString`() {
        val result = GBValue.from(JsonPrimitive("hello"))
        assertEquals(GBString("hello"), result)
    }

    @Test
    fun `from converts JsonPrimitive int to GBNumber`() {
        val result = GBValue.from(JsonPrimitive(42))
        assertEquals(GBNumber(42), result)
    }

    @Test
    fun `from converts JsonArray to GBArray`() {
        val result = GBValue.from(JsonArray(listOf(JsonPrimitive(1))))
        assertEquals(GBArray(listOf(GBNumber(1))), result)
    }

    @Test
    fun `from converts JsonObject to GBJson`() {
        val result = GBValue.from(JsonObject(mapOf("id" to JsonPrimitive(1))))
        assertEquals(GBJson(mapOf("id" to GBNumber(1))), result)
    }

    @Test
    fun `from and gbSerialize are inverse operations`() {
        val original = GBJson(mapOf("x" to GBNumber(1)))

        val serialized = original.gbSerialize()  // GBJson → JsonObject
        val result = GBValue.from(serialized)    // JsonObject → GBJson

        assertEquals(original, result)
    }
}

@Serializable
data class OrderTest(val user: TestConfig, val total: Int)

@Serializable
data class TestConfig(val price: Int, val label: String)
