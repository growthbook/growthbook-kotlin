package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GBNumberTests {

    private val integerNumbers: List<GBNumber> =
        listOf(123, 123L, 123.toByte(), 123.toShort()).map(::GBNumber)

    private val floatNumbers: List<GBNumber> = listOf(123f, 123.0).map(::GBNumber)

    private val floatingPointErrorNumbers: List<GBNumber> =
        listOf(0.1 + 0.2, 0.3, 0.1f + 0.2f).map(::GBNumber)

    @Test
    fun testEquals() {
        assertAllPairsEqual(integerNumbers)
        assertAllPairsEqual(floatNumbers)
        assertAllPairsEqual(floatingPointErrorNumbers)
    }

    @Test
    fun testHashCode() {
        assertAllPairsEqual(integerNumbers.map { it.hashCode() })
        assertAllPairsEqual(floatNumbers.map { it.hashCode() })
        assertAllPairsEqual(floatingPointErrorNumbers.map { it.hashCode() })
    }

    @Test
    fun testGrowthBookArrayContains() {
        floatingPointErrorNumbers.forEach { number1 ->
            floatingPointErrorNumbers.forEach { number2 ->
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
