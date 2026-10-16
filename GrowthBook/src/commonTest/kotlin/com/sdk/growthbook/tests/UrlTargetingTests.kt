package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.GBUrlTarget
import com.sdk.growthbook.utils.GBUrlTargetType
import com.sdk.growthbook.utils.url.isUrlTargeted
import com.sdk.growthbook.utils.url.mergeQueryStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from the reference JS SDK's `packages/sdk-js/test/visual-changes.test.ts`.
 *
 * Deliberately in `commonTest` rather than `jvmTest`: this is the one piece of the SDK that leans on
 * platform `Regex` behaviour and on a hand-rolled URL parser, so it has to run on JS, wasmJs and
 * Native too.
 */
class UrlTargetingTests {

    private fun include(pattern: String, type: GBUrlTargetType = GBUrlTargetType.SIMPLE) =
        GBUrlTarget(type = type, pattern = pattern, include = true)

    private fun exclude(pattern: String, type: GBUrlTargetType = GBUrlTargetType.SIMPLE) =
        GBUrlTarget(type = type, pattern = pattern, include = false)

    @Test
    fun testNoTargetingRules() {
        assertFalse(isUrlTargeted("https://example.com/testing", emptyList()))
        assertFalse(isUrlTargeted("https://example.com/testing", null))
    }

    @Test
    fun testMixOfIncludeAndExcludeRules() {
        val url = "https://www.example.com"
        val includeMatch = include(url)
        val excludeMatch = exclude(url)
        val includeNoMatch = include("https://wrong.com")
        val excludeNoMatch = exclude("https://another.com")

        // One include rule matches, one exclude rule matches
        assertFalse(
            isUrlTargeted(url, listOf(includeMatch, includeNoMatch, excludeMatch, excludeNoMatch))
        )
        // One include rule matches, no exclude rule matches
        assertTrue(isUrlTargeted(url, listOf(includeMatch, includeNoMatch, excludeNoMatch)))
        // No include rule matches, no exclude rule matches
        assertFalse(isUrlTargeted(url, listOf(includeNoMatch, excludeNoMatch)))
        // No include rule matches, one exclude rule matches
        assertFalse(isUrlTargeted(url, listOf(includeNoMatch, excludeNoMatch, excludeMatch)))
        // Only exclude rules, none matches
        assertTrue(isUrlTargeted(url, listOf(excludeNoMatch, excludeNoMatch)))
        // Only exclude rules, one matches
        assertFalse(isUrlTargeted(url, listOf(excludeNoMatch, excludeMatch)))
        // Only include rules, none matches
        assertFalse(isUrlTargeted(url, listOf(includeNoMatch, includeNoMatch)))
        // Only include rules, one matches
        assertTrue(isUrlTargeted(url, listOf(includeNoMatch, includeMatch)))
    }

    @Test
    fun testExcludeRuleOnTopOfIncludeRule() {
        val rules = listOf(include("/search"), exclude("/search?bad=true"))

        assertTrue(isUrlTargeted("https://example.com/search", rules))
        assertFalse(isUrlTargeted("https://example.com/search?bad=true", rules))
        assertTrue(isUrlTargeted("https://example.com/search?good=true", rules))
    }

    @Test
    fun testUrlTargetingCases() {
        val simple = GBUrlTargetType.SIMPLE
        val regex = GBUrlTargetType.REGEX
        val cases = listOf(
            Case(regex, "https://www.example.com/post/123", "^/post/[0-9]+", true),
            Case(regex, "https://www.example.com/post/abc", "^/post/[0-9]+", false),
            Case(regex, "https://www.example.com/new/post/123", "^/post/[0-9]+", false),
            Case(regex, "https://www.example.com/new/post/123", "example\\.com.*/post/[0-9]+", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo?baz=2", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo?foo=3", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/bar?baz=2", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "foo", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "*?baz=2&bar=1", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "*.example.com/foo", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "blah.example.com/foo", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "https://www.*.com/foo", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "*.example.com", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "http://www.example.com/foo", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "f", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "f*", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "*f*", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo/", true),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo/bar", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/foo/bar", false),
            Case(simple, "https://www.example.com/foo?bar=1&baz=2", "/bar/foo", false),
            Case(simple, "https://www.example.com/foo/bar/baz", "/foo/*/baz", true),
            Case(simple, "https://www.example.com/foo/bar/(baz", "/foo/*", true),
            Case(simple, "https://www.example.com/foo/bar/#test", "/foo/*", true),
            Case(simple, "https://www.example.com/foo/#test", "/foo/", true),
            Case(simple, "https://www.example.com/foo/#test", "/foo/#test", true),
            Case(simple, "https://www.example.com/foo/#test", "/foo/#blah", false),
            Case(simple, "/foo/bar/?baz=1", "http://example.com/foo/bar", false),
            Case(simple, "/foo/bar/?baz=1", "/foo/bar", true),
            Case(simple, "&??*&&(", "/foo/bar", false),
            Case(simple, "&??*&&(", "((*)(*\$&#@!!)))", false),
        )

        cases.forEachIndexed { index, case ->
            assertEquals(
                case.expected,
                isUrlTargeted(case.url, listOf(include(case.pattern, case.type))),
                "case $index: ${case.type} url: `${case.url}` pattern: `${case.pattern}`"
            )
        }
    }

    @Test
    fun testUnrecognisedTargetTypeFailsClosed() {
        val url = "https://www.example.com/foo"

        assertFalse(
            isUrlTargeted(url, listOf(GBUrlTarget(type = GBUrlTargetType.UNKNOWN, pattern = "/foo")))
        )
        assertFalse(isUrlTargeted(url, listOf(GBUrlTarget(type = null, pattern = "/foo"))))
    }

    @Test
    fun testInvalidRegexPatternDoesNotMatch() {
        assertFalse(
            isUrlTargeted(
                "https://www.example.com/foo",
                listOf(include("[unterminated", GBUrlTargetType.REGEX))
            )
        )
    }

    @Test
    fun testMergeQueryStrings() {
        assertEquals(
            "http://www.example.com/home-new",
            mergeQueryStrings("http://www.example.com/home", "http://www.example.com/home-new")
        )
        assertEquals(
            "http://www.example.com/home-new?color=blue&food=sushi",
            mergeQueryStrings(
                "http://www.example.com/home?color=blue&food=sushi",
                "http://www.example.com/home-new"
            )
        )
        // Params already on the redirect URL win; the rest are appended in their original order.
        assertEquals(
            "http://www.example.com/home-new?name=test&color=red&food=lasagna&title=original",
            mergeQueryStrings(
                "http://www.example.com/home?color=blue&food=sushi&title=original",
                "http://www.example.com/home-new?name=test&color=red&food=lasagna"
            )
        )
    }

    @Test
    fun testMergeQueryStringsKeepsRepeatedAndEmptyKeys() {
        // The destination's own query string survives verbatim. A map-backed merge would collapse
        // `filter=red&filter=blue` to `filter=red`, silently dropping half of a destination URL the
        // experiment author wrote.
        assertEquals(
            "http://www.example.com/home-new?filter=red&filter=blue&page=2",
            mergeQueryStrings(
                "http://www.example.com/home?page=2",
                "http://www.example.com/home-new?filter=red&filter=blue"
            )
        )
        // From the original side only the first pair per unused key is carried over, exactly as the
        // reference SDK's has()/set() pair does.
        assertEquals(
            "http://www.example.com/home-new?tag=a",
            mergeQueryStrings(
                "http://www.example.com/home?tag=a&tag=b",
                "http://www.example.com/home-new"
            )
        )
        // …and a key already on the destination blocks every copy of it from the original.
        assertEquals(
            "http://www.example.com/home-new?tag=x",
            mergeQueryStrings(
                "http://www.example.com/home?tag=a&tag=b",
                "http://www.example.com/home-new?tag=x"
            )
        )
        // An empty key is a legal pair, not something to drop.
        assertEquals(
            "http://www.example.com/home-new?=x",
            mergeQueryStrings("http://www.example.com/home?=x", "http://www.example.com/home-new")
        )
    }

    @Test
    fun testMergeQueryStringsFallsBackWhenUrlIsNotAbsolute() {
        assertEquals("/home-new", mergeQueryStrings("http://www.example.com/home?a=1", "/home-new"))
        assertEquals(
            "http://www.example.com/home-new",
            mergeQueryStrings("/home?a=1", "http://www.example.com/home-new")
        )
        assertEquals(
            "http://www.example.com/home-new",
            mergeQueryStrings(null, "http://www.example.com/home-new")
        )
    }

    private data class Case(
        val type: GBUrlTargetType,
        val url: String,
        val pattern: String,
        val expected: Boolean,
    )
}
