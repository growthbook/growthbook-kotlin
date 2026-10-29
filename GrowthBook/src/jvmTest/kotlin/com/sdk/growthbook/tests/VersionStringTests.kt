package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Version parsing for the `$v*` operators, ported from the JS SDK's tests for growthbook/growthbook#7401:
 * a leading `v` and build metadata are stripped without a regex.
 */
class VersionStringTests {

    private val evaluator = GBConditionEvaluator()

    private fun padded(input: String) = GBUtils.paddedVersionString(input)

    private fun eval(condition: String, version: String) =
        evaluator.evalCondition(
            mapOf("v" to GBString(version)),
            GBValue.from(Json.parseToJsonElement(condition)) as GBJson,
            null
        )

    @Test
    fun padsNumericPartsAndAppendsTildeToPlainVersions() {
        assertEquals("    1-    2-    3-~", padded("1.2.3"))
        assertEquals("   10-   12-   13-~", padded("10.12.13"))
    }

    @Test
    fun stripsOneLeadingV() {
        assertEquals(padded("1.2.3"), padded("v1.2.3"))
        // Only a single leading v is removed, and only at the start
        assertEquals("v1-    2-    3-~", padded("vv1.2.3"))
        assertEquals("    1-    2-v3-~", padded("1.2.v3"))
    }

    @Test
    fun stripsBuildMetadataAfterTheFirstPlus() {
        assertEquals(padded("1.2.3"), padded("1.2.3+build"))
        assertEquals(padded("1.2.3"), padded("v1.2.3+build+more.1-2"))
        assertEquals(padded("1.2.3"), padded("1.2.3+"))
        assertEquals("", padded("+build"))
    }

    @Test
    fun keepsPreReleaseTagsBelowTheRelease() {
        assertEquals("    1-    2-    3-rc-    1", padded("v1.2.3-rc.1+build123"))
        assertTrue(padded("1.0.0-beta") < padded("1.0.0"))
    }

    @Test
    fun cutsAtTheFirstPlusEvenWhenANewlineFollows() {
        // The regex kept "+build\nmore": `.` doesn't match a newline
        assertEquals(padded("1.2.3"), padded("1.2.3+build\nmore"))
        assertEquals(padded("1.2.3"), padded("1.2.3+\n"))
    }

    @Test
    fun runsInLinearTimeOnManyPlusCharacters() {
        // Quadratic for the regex on the JVM: every `+` started a `.*` that failed at "\nx"
        val input = "+".repeat(50_000) + "\nx"
        val start = System.nanoTime()
        padded(input)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs < 500, "took $elapsedMs ms")
    }

    @Test
    fun versionOperatorsIgnoreALeadingVAndBuildMetadata() {
        assertTrue(eval("""{"v": {"${'$'}veq": "1.2.3"}}""", "v1.2.3+build"))
        assertTrue(eval("""{"v": {"${'$'}vgt": "1.2.3"}}""", "1.2.4+build"))
        assertTrue(eval("""{"v": {"${'$'}vlt": "1.2.3"}}""", "1.2.3-rc.1"))
        assertFalse(eval("""{"v": {"${'$'}vgt": "1.2.3+a"}}""", "1.2.3+z"))
    }
}
