package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class GBNumberTests {

    private val integerNumbers = listOf(123, 123L, 123.toByte(), 123.toShort()).map { GBNumber(it) }
    private val floatNumbers = listOf(123f, 123.0).map { GBNumber(it) }

    @Test
    fun testEquals() {
        assertAllPairsEqual(integerNumbers)
        assertAllPairsEqual(floatNumbers)
    }

    @Test
    fun testHashCode() {
        assertAllPairsEqual(integerNumbers.map { it.hashCode() })
        assertAllPairsEqual(floatNumbers.map { it.hashCode() })
    }

    private fun assertAllPairsEqual(values: List<*>) {
        values.forEach { value1 ->
            values.forEach { value2 ->
                assertEquals(value1, value2)
            }
        }
    }

}
