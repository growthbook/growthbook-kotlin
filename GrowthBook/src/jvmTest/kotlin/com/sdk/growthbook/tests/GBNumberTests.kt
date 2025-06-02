package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.toGbNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GBNumberTests {

    private val integerNumbers: List<GBNumber> =
        listOf(
            123.toGbNumber(),
            123L.toGbNumber(),
            123.toByte().toGbNumber(),
            123.toShort().toGbNumber()
        )

    private val floatNumbers: List<GBNumber> =
        listOf(
            123f.toGbNumber(),
            123.0.toGbNumber()
        )

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

    @Test
    fun testGrowthBookArrayContains() {
        integerNumbers.forEach { number1 ->
            integerNumbers.forEach { number2 ->
                assertTrue(GBArray(listOf(number1)).contains(number2))
            }
        }
        floatNumbers.forEach { number1 ->
            floatNumbers.forEach { number2 ->
                assertTrue(GBArray(listOf(number1)).contains(number2))
            }
        }
    }

    private fun assertAllPairsEqual(values: List<*>) {
        values.forEach { value1 ->
            values.forEach { value2 ->
                assertEquals(value1, value2)
            }
        }
    }

}
