package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.jsNumberToString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lives in `commonTest` so the JS `String(number)` formatter is checked on every target: the
 * platforms' own `Double.toString` differ (JVM before JDK 19 prints `1e23` as
 * `9.999999999999999E22`). Expected values come from Node's `String(x)`.
 */
class JsNumberToStringTest {

    @Test
    fun formatsLikeJsString() {
        val cases = listOf(
            1.0 to "1",
            1.5 to "1.5",
            1e21 to "1e+21",
            1e-7 to "1e-7",
            0.000002 to "0.000002",
            123e-20 to "1.23e-18",
            1e23 to "1e+23",
            -0.0 to "0",
            5e-324 to "5e-324",
            Double.MAX_VALUE to "1.7976931348623157e+308",
            5.684341886080802e-14 to "5.684341886080802e-14",
            8.41e21 to "8.41e+21",
            9.223372036854776e18 to "9223372036854776000",
            1e20 to "100000000000000000000",
            1.2345678901234568e20 to "123456789012345680000",
            0.1 + 0.2 to "0.30000000000000004",
            4.35 to "4.35",
            0.000001 to "0.000001",
            -1.5e300 to "-1.5e+300",
            Double.NaN to "NaN",
            Double.NEGATIVE_INFINITY to "-Infinity",
        )
        for ((number, expected) in cases) {
            assertEquals(expected, jsNumberToString(number), "String($number)")
        }
    }
}
