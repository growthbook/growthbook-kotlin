package com.sdk.growthbook.utils

import com.sdk.growthbook.network.GBRequestHeaders

/**
 * Validates host/header options once, when the SDK is built, so misconfigurations fail fast with a
 * clear message rather than turning into an opaque fetch failure at runtime.
 *
 * All independent problems are collected and reported together (see [GBInvalidOptionsException]).
 * Only header *names* ever appear in a message — values may contain secrets.
 */
internal object GBOptionsValidator {

    private const val SCHEME_SEPARATOR = "://"

    /**
     * @throws GBInvalidOptionsException listing every problem found.
     */
    fun validate(
        streamingHost: String?,
        apiHostRequestHeaders: Map<String, String>?,
        streamingHostRequestHeaders: Map<String, String>?,
        apiHost: String? = null,
    ) {
        val violations = findViolations(
            streamingHost,
            apiHostRequestHeaders,
            streamingHostRequestHeaders,
            apiHost,
        )
        if (violations.isNotEmpty()) {
            throw GBInvalidOptionsException(
                "Invalid GrowthBook options: ${violations.joinToString("; ")}",
                violations,
            )
        }
    }

    /**
     * Collects every validation problem without throwing.
     */
    fun findViolations(
        streamingHost: String?,
        apiHostRequestHeaders: Map<String, String>?,
        streamingHostRequestHeaders: Map<String, String>?,
        apiHost: String? = null,
    ): List<String> = buildList {
        checkHost("apiHost", apiHost, this)
        checkHost("streamingHost", streamingHost, this)
        checkRequestHeaders("apiHostRequestHeaders", apiHostRequestHeaders, this)
        checkRequestHeaders("streamingHostRequestHeaders", streamingHostRequestHeaders, this)
    }

    /**
     * Accepts a host with or without a scheme (a scheme-less `cdn.example.com` is normalised to
     * https by [com.sdk.growthbook.features.FeatureURLBuilder]); rejects a scheme other than
     * http/https and anything without a usable host part.
     *
     * A blank host means "not set", not "misconfigured": build configs routinely default such a
     * value to the empty string, and every consumer of it already falls back. Failing the build
     * over it would break existing apps on a minor upgrade.
     */
    private fun checkHost(optionName: String, host: String?, violations: MutableList<String>) {
        if (host.isNullOrBlank()) return

        val raw = host.trim()
        val schemeSeparatorIndex = raw.indexOf(SCHEME_SEPARATOR)
        if (schemeSeparatorIndex >= 0) {
            val scheme = raw.substring(0, schemeSeparatorIndex).lowercase()
            if (scheme != "http" && scheme != "https") {
                violations.add("$optionName must use the http or https scheme: $host")
                return
            }
        }

        val authority = raw
            .substring(if (schemeSeparatorIndex >= 0) schemeSeparatorIndex + SCHEME_SEPARATOR.length else 0)
            .takeWhile { it != '/' && it != '?' && it != '#' }
        // Drop userinfo and port, leaving the host itself.
        val hostname = authority.substringAfterLast('@').substringBefore(':')
        if (hostname.isBlank() || hostname.any { it.isWhitespace() }) {
            violations.add("$optionName is not a valid URL: $host")
        }
    }

    /**
     * Rejects the SDK-managed names in [GBRequestHeaders.RESERVED] (overriding `If-None-Match` or
     * `Cache-Control` would break ETag-based revalidation) plus names and values no HTTP client can
     * send. Both are checked here so the consumer hears about it once, at build time, instead of
     * every request silently losing the header.
     *
     * A header **value** is never quoted in a violation message — only its name. Values carry
     * credentials, and this message ends up in [GBInvalidOptionsException] and, via the SDK's error
     * path, in the log.
     */
    private fun checkRequestHeaders(
        optionName: String,
        headers: Map<String, String>?,
        violations: MutableList<String>,
    ) {
        if (headers.isNullOrEmpty()) return

        for ((name, value) in headers) {
            when {
                name.isBlank() ->
                    violations.add("$optionName must not contain a blank header name")

                GBRequestHeaders.isReserved(name) -> violations.add(
                    "$optionName must not contain the reserved header '${name.trim()}'; " +
                        "If-None-Match and Cache-Control are managed by the SDK"
                )

                !GBRequestHeaders.isValidName(name) -> violations.add(
                    "$optionName contains an invalid header name '${name.trim()}': " +
                        "header names must be a valid HTTP token"
                )

                !GBRequestHeaders.isValidValue(value) -> violations.add(
                    "$optionName contains an invalid value for header '${name.trim()}': " +
                        "header values must not contain control characters, line breaks or " +
                        "non-ASCII characters (the value itself is omitted here — it may be a " +
                        "credential)"
                )
            }
        }
    }
}
