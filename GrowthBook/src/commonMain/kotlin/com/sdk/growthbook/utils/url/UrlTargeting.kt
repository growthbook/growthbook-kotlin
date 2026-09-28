package com.sdk.growthbook.utils.url

import com.sdk.growthbook.utils.GBUrlTarget
import com.sdk.growthbook.utils.GBUrlTargetType

/**
 * `*` is not a valid character in a hostname, so the reference SDK swaps it for this placeholder
 * before parsing a pattern and turns it back into `.*` once the pattern is escaped for a regex.
 */
internal const val WILDCARD_PLACEHOLDER = "_____"

private const val REGEX_LITERALS = "*.+?^\${}()|[]\\"

/**
 * URL targeting, ported from the reference JS SDK (`packages/sdk-js/src/util.ts`) so that
 * `experiment.urlPatterns` behaves identically across SDKs.
 *
 * A URL is targeted when no exclude rule matches and either at least one include rule matches or
 * there are no include rules at all. An empty rule list is never targeted.
 */
internal fun isUrlTargeted(url: String?, targets: List<GBUrlTarget>?): Boolean {
    if (targets.isNullOrEmpty()) return false

    val safeUrl = url ?: ""
    var hasIncludeRules = false
    var isIncluded = false

    for (target in targets) {
        val match = evalUrlTarget(safeUrl, target.type, target.pattern)
        // Only an explicit `false` excludes, as in the reference SDK (`targets[i].include === false`);
        // an omitted flag is an include rule.
        if (target.include == false) {
            if (match) return false
        } else {
            hasIncludeRules = true
            if (match) isIncluded = true
        }
    }

    return isIncluded || !hasIncludeRules
}

/**
 * Compiles a `regex` targeting pattern, escaping unescaped forward slashes the way the JS SDK does.
 * Returns null when the pattern is not a valid regular expression, which makes the target fail to
 * match rather than breaking evaluation.
 *
 * Fails silently rather than logging: these helpers are pure and run on every evaluation, so they
 * stay free of the logger (which is platform-backed and, on Android, unavailable to plain unit
 * tests). Logging happens at the call sites that have a context to gate it on.
 */
internal fun getUrlRegExp(pattern: String): Regex? =
    try {
        Regex(escapeForwardSlashes(pattern))
    } catch (throwable: Throwable) {
        null
    }

/**
 * Merges the query string of [oldUrl] into [newUrl], implementing `experiment.persistQueryString`.
 * Parameters already on [newUrl] win; the rest are appended in their original order. Returns
 * [newUrl] unchanged when either URL is not absolute — the reference SDK parses both without a base
 * and bails out on the resulting error.
 */
internal fun mergeQueryStrings(oldUrl: String?, newUrl: String): String {
    val current = parseAbsoluteUrl(oldUrl ?: "")
    val redirect = parseAbsoluteUrl(newUrl)
    if (current == null || redirect == null) return newUrl

    // Pairs, not a map: the destination's own query string has to survive verbatim, and a map would
    // drop a repeated key from it (`?filter=red&filter=blue` becoming `?filter=red`).
    val merged = redirect.queryPairs().toMutableList()
    // `seen.add(key)` is exactly the reference's `!redirectUrl.searchParams.has(key)` guard: the
    // first pair from the original URL under an unused key is carried over, later ones under that
    // same key are not — the destination is only ever extended, never rewritten.
    val seen = merged.mapTo(mutableSetOf()) { (key, _) -> key }
    for ((key, value) in current.queryPairs()) {
        if (seen.add(key)) {
            merged.add(key to value)
        }
    }

    return buildString {
        append(redirect.origin)
        append(redirect.pathname)
        if (merged.isNotEmpty()) {
            append('?')
            append(merged.joinToString("&") { (key, value) -> "$key=$value" })
        }
        if (redirect.fragment.isNotEmpty()) {
            append('#').append(redirect.fragment)
        }
    }
}

private fun evalUrlTarget(url: String, type: GBUrlTargetType?, pattern: String?): Boolean {
    // Fail closed on a target with no pattern, for the same reason as an unrecognised type below.
    if (pattern == null) return false

    val parsed = parseUrl(url, RELATIVE_URL_BASE) ?: return false

    return when (type) {
        GBUrlTargetType.REGEX -> {
            val regex = getUrlRegExp(pattern) ?: return false
            val href = parsed.href
            regex.containsMatchIn(href) ||
                regex.containsMatchIn(href.substring(parsed.origin.length))
        }

        GBUrlTargetType.SIMPLE -> evalSimpleUrlTarget(parsed, pattern)

        // Fail closed: only an explicit, recognised type can match. An absent type — or a future API
        // type this version does not know — must never fall through to `simple` and enrol the user,
        // which would fire an exposure for an experiment that was not meant to run here.
        GBUrlTargetType.UNKNOWN, null -> false
    }
}

private fun evalSimpleUrlTarget(actual: ParsedUrl, pattern: String): Boolean {
    val expected = parseUrl(
        prefixSchemeIfHostLike(pattern).replace("*", WILDCARD_PLACEHOLDER),
        RELATIVE_PATTERN_BASE,
    ) ?: return false

    if (!evalSimpleUrlPart(actual.host, expected.host, isPath = false)) return false
    if (!evalSimpleUrlPart(actual.pathname, expected.pathname, isPath = true)) return false

    // The fragment is only compared when the pattern explicitly targets one.
    if (expected.fragment.isNotEmpty() &&
        !evalSimpleUrlPart(actual.fragment, expected.fragment, isPath = false)
    ) {
        return false
    }

    // Every pair of the pattern is compared against the *first* value the URL has under that key —
    // `actual.searchParams.get(k) || ""` in the reference, hence the lookup view on one side and
    // pairs on the other.
    val actualParams = actual.queryLookup()
    for ((key, expectedValue) in expected.queryPairs()) {
        if (!evalSimpleUrlPart(actualParams[key] ?: "", expectedValue, isPath = false)) {
            return false
        }
    }

    return true
}

private fun evalSimpleUrlPart(actual: String, pattern: String, isPath: Boolean): Boolean {
    return try {
        var escaped = escapeRegexLiterals(pattern).replace(WILDCARD_PLACEHOLDER, ".*")
        if (isPath) {
            // When matching a path, leading and trailing slashes are optional on both sides.
            escaped = "\\/?" + escaped.removePrefix("/").removeSuffix("/") + "\\/?"
        }
        Regex("^" + escaped + "$", RegexOption.IGNORE_CASE).containsMatchIn(actual)
    } catch (throwable: Throwable) {
        false
    }
}

/**
 * Prepends `https://` when the pattern starts with something host-like — a run of characters
 * containing a dot and none of `:`, `/` or `?`. Without it `www.example.com/foo` would parse as a
 * relative path.
 */
private fun prefixSchemeIfHostLike(pattern: String): String {
    val prefixEnd = pattern.indexOfFirst { it == ':' || it == '/' || it == '?' }
    val prefix = if (prefixEnd < 0) pattern else pattern.substring(0, prefixEnd)
    return if (prefix.contains('.')) "https://$pattern" else pattern
}

/** Escapes the characters that would otherwise be regex syntax. */
private fun escapeRegexLiterals(input: String): String = buildString {
    for (char in input) {
        if (char in REGEX_LITERALS) {
            append('\\')
        }
        append(char)
    }
}

/**
 * Escapes every forward slash that is not already escaped. Mirrors the JS SDK's
 * `regexString.replace(/([^\\])\//g, "$1\\/")`, including the way a global regex consumes the
 * character preceding each match.
 */
private fun escapeForwardSlashes(input: String): String = buildString {
    var index = 0
    while (index < input.length) {
        val char = input[index]
        if (char != '\\' && index + 1 < input.length && input[index + 1] == '/') {
            append(char).append("\\/")
            index += 2
        } else {
            append(char)
            index++
        }
    }
}
