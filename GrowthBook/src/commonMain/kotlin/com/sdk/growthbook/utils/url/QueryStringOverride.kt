package com.sdk.growthbook.utils.url

/**
 * Reads a forced variation for [id] out of the page URL's query string — `?my-experiment=1` — as the
 * reference JS SDK's `getQueryStringOverride` does. Used for QA links.
 *
 * Returns null when the URL carries no usable override: no query string, no matching parameter, a
 * non-numeric value, or an index outside `0 until numVariations`.
 */
internal fun getQueryStringOverride(id: String, url: String?, numVariations: Int): Int? {
    if (url.isNullOrEmpty()) return null

    // Not substringAfter('?'): the reference SDK takes the segment between the first and second `?`,
    // so a URL like `http://example.com??a=1` has an empty query string and yields no override.
    val search = url.split("?").getOrNull(1)
    if (search.isNullOrEmpty()) return null

    val withoutAnchor = search.substringBefore('#')
    for (pair in withoutAnchor.split("&")) {
        val separator = pair.indexOf('=')
        val key = if (separator < 0) pair else pair.substring(0, separator)
        if (key != id) continue

        // Only the first matching parameter is considered, and a valueless one disqualifies the
        // override outright rather than deferring to a later duplicate.
        if (separator < 0) return null
        val variation = parseLeadingInt(pair.substring(separator + 1)) ?: return null
        return variation.takeIf { it in 0..<numVariations }
    }

    return null
}

/**
 * Mirrors JavaScript's `parseInt`: an optional sign followed by leading decimal digits, ignoring
 * whatever trails them (`"2.054"` is 2). Returns null when there are no digits to read.
 */
private fun parseLeadingInt(value: String): Int? {
    val trimmed = value.trimStart()
    var end = 0
    if (end < trimmed.length && (trimmed[end] == '+' || trimmed[end] == '-')) {
        end++
    }
    val digitsStart = end
    while (end < trimmed.length && trimmed[end] in '0'..'9') {
        end++
    }
    if (end == digitsStart) return null

    return trimmed.substring(0, end).toIntOrNull()
}
