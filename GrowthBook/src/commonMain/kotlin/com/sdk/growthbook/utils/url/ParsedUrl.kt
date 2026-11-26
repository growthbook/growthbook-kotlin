package com.sdk.growthbook.utils.url

/**
 * Minimal, dependency-free URL representation used by URL targeting.
 *
 * Kotlin has no multiplatform URL parser and this module cannot depend on Ktor (the network
 * dispatcher is injected by the consumer, so Ktor is optional), which is why this hand-rolls just
 * enough of what the reference JS SDK gets from `new URL(input, base)`: scheme / authority / path /
 * query / fragment, plus resolution of a relative input against a base.
 *
 * Deliberately *not* implemented, because URL targeting never observes the difference:
 * percent-encoding normalisation, default-port stripping and dot-segment collapsing.
 */
internal data class ParsedUrl(
    val scheme: String,
    val authority: String,
    val path: String,
    val query: String,
    val fragment: String,
) {

    /**
     * Authority without any `user:password@` prefix — mirrors `URL.host`, which keeps the port but
     * drops user info.
     */
    val host: String
        get() = authority.substringAfterLast('@')

    /**
     * Mirrors `URL.pathname`, which is never empty once there is an authority: `https://example.com`
     * parses to a pathname of `/`.
     */
    val pathname: String
        get() = if (path.isEmpty() && authority.isNotEmpty()) "/" else path

    /** Mirrors `URL.origin`. Scheme and authority are lower-cased, as the JS parser does. */
    val origin: String
        get() = if (authority.isEmpty()) {
            "${scheme.lowercase()}:"
        } else {
            "${scheme.lowercase()}://${authority.lowercase()}"
        }

    /** Mirrors `URL.href`. Kept consistent with [origin] so `href.substring(origin.length)` works. */
    val href: String
        get() = buildString {
            append(origin)
            append(pathname)
            if (query.isNotEmpty()) {
                append('?').append(query)
            }
            if (fragment.isNotEmpty()) {
                append('#').append(fragment)
            }
        }

    /**
     * Query parameters as an ordered list of pairs, mirroring `URLSearchParams` itself: a repeated
     * key stays two entries and an empty key is a legal pair. Anything that has to *reproduce* a
     * query string — merging for `persistQueryString` — must work from this, not from a map, which
     * would silently collapse both.
     */
    fun queryPairs(): List<Pair<String, String>> = parseQueryPairs(query)

    /**
     * First value per key, mirroring `URLSearchParams.get()` — what looking one parameter up
     * returns. For *reading* a single parameter this is the faithful view; see [queryPairs] for the
     * rest.
     */
    fun queryLookup(): Map<String, String> {
        val lookup = LinkedHashMap<String, String>()
        for ((key, value) in queryPairs()) {
            if (!lookup.containsKey(key)) {
                lookup[key] = value
            }
        }
        return lookup
    }
}

/** Base the reference SDK resolves a relative *target* URL against (`_evalURLTarget`). */
internal val RELATIVE_URL_BASE = ParsedUrl(
    scheme = "https", authority = "_", path = "", query = "", fragment = ""
)

/**
 * Base the reference SDK resolves a relative *pattern* against (`_evalSimpleUrlTarget`). It differs
 * from [RELATIVE_URL_BASE] on purpose: `_____` is the wildcard placeholder, so a pattern that omits
 * the host matches any host.
 */
internal val RELATIVE_PATTERN_BASE = ParsedUrl(
    scheme = "https", authority = WILDCARD_PLACEHOLDER, path = "", query = "", fragment = ""
)

private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+\\-.]*:")

/**
 * Parses [raw], resolving it against [base] when it is relative. Returns null for input the JS
 * parser would reject (which makes the caller treat the target as not matching).
 */
internal fun parseUrl(raw: String, base: ParsedUrl): ParsedUrl? {
    val input = raw.trim()
    val schemeMatch = SCHEME_PREFIX.find(input)

    if (schemeMatch != null) {
        val scheme = input.substring(0, schemeMatch.value.length - 1)
        val rest = input.substring(schemeMatch.value.length)
        return parseAfterScheme(scheme, rest)
    }

    // Protocol-relative: `//host/path` keeps the base scheme but brings its own authority.
    if (input.startsWith("//")) {
        return parseAfterScheme(base.scheme, input)
    }

    val (path, query, fragment) = splitPathQueryFragment(input)
    val resolvedPath = when {
        path.startsWith("/") -> path
        path.isEmpty() -> base.pathname
        else -> {
            val basePath = base.pathname
            basePath.substring(0, basePath.lastIndexOf('/') + 1) + path
        }
    }
    // A fragment- or query-only input keeps the base query, as the WHATWG algorithm does.
    val resolvedQuery = if (path.isEmpty() && query.isEmpty()) base.query else query

    return ParsedUrl(
        scheme = base.scheme,
        authority = base.authority,
        path = resolvedPath,
        query = resolvedQuery,
        fragment = fragment,
    )
}

/**
 * Parses [raw] only if it is absolute (carries its own scheme), mirroring `new URL(input)` with no
 * base. Returns null for a relative input, where the JS parser throws.
 */
internal fun parseAbsoluteUrl(raw: String): ParsedUrl? {
    val input = raw.trim()
    val schemeMatch = SCHEME_PREFIX.find(input) ?: return null
    return parseAfterScheme(
        scheme = input.substring(0, schemeMatch.value.length - 1),
        rest = input.substring(schemeMatch.value.length),
    )
}

private fun parseAfterScheme(scheme: String, rest: String): ParsedUrl? {
    if (!rest.startsWith("//")) {
        // Opaque (`mailto:…`): everything after the scheme is the path.
        val (path, query, fragment) = splitPathQueryFragment(rest)
        return ParsedUrl(scheme, authority = "", path = path, query = query, fragment = fragment)
    }

    val afterSlashes = rest.substring(2)
    val authorityEnd = afterSlashes.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority =
        if (authorityEnd < 0) afterSlashes else afterSlashes.substring(0, authorityEnd)
    // `new URL("https://")` throws; an empty authority after `//` is not a valid URL.
    if (authority.isEmpty()) return null

    val remainder = if (authorityEnd < 0) "" else afterSlashes.substring(authorityEnd)
    val (path, query, fragment) = splitPathQueryFragment(remainder)
    return ParsedUrl(scheme, authority, path, query, fragment)
}

/** Splits `path?query#fragment`. The fragment is taken first — it may itself contain a `?`. */
private fun splitPathQueryFragment(input: String): Triple<String, String, String> {
    val hashIndex = input.indexOf('#')
    val fragment = if (hashIndex < 0) "" else input.substring(hashIndex + 1)
    val withoutFragment = if (hashIndex < 0) input else input.substring(0, hashIndex)

    val questionIndex = withoutFragment.indexOf('?')
    val query = if (questionIndex < 0) "" else withoutFragment.substring(questionIndex + 1)
    val path = if (questionIndex < 0) withoutFragment else withoutFragment.substring(0, questionIndex)

    return Triple(path, query, fragment)
}

/**
 * Parses a raw query string into ordered key/value pairs, keeping repeated and empty keys — the
 * `application/x-www-form-urlencoded` parsing the browser applies to `URLSearchParams`. A key with
 * no `=` yields an empty value, and an empty sequence (`a=1&&b=2`) is skipped.
 */
internal fun parseQueryPairs(rawQuery: String): List<Pair<String, String>> {
    if (rawQuery.isEmpty()) return emptyList()

    val pairs = mutableListOf<Pair<String, String>>()
    for (pair in rawQuery.split('&')) {
        if (pair.isEmpty()) continue
        val separator = pair.indexOf('=')
        val key = if (separator < 0) pair else pair.substring(0, separator)
        val value = if (separator < 0) "" else pair.substring(separator + 1)
        pairs.add(key to value)
    }
    return pairs
}
