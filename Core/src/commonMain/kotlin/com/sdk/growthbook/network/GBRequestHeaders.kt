package com.sdk.growthbook.network

/**
 * Helpers for the custom request headers a consumer can attach to API and streaming requests
 * (`apiHostRequestHeaders` / `streamingHostRequestHeaders`).
 *
 * Two header names are **SDK-managed**: the SDK sets them itself and a consumer-supplied value for
 * them is dropped rather than merged, because overriding `If-None-Match` / `Cache-Control` would
 * break the ETag-based revalidation the SDK relies on. `User-Agent` is deliberately *not* reserved:
 * the SDK never sets it on the requests these headers apply to, so blocking it would only break
 * consumers behind a gateway that requires a specific one.
 *
 * Header **values** may carry credentials (a gateway token, a signed cookie). Never log them —
 * only names are ever safe to log. This is also why unusable values are dropped here instead of
 * being handed to the HTTP client, whose exception message would quote them back.
 */
object GBRequestHeaders {

    /**
     * Lower-cased names of the headers the SDK manages itself. Consumer-supplied entries for these
     * are ignored. HTTP header names are case-insensitive, so compare in lower case.
     */
    val RESERVED: Set<String> = setOf("if-none-match", "cache-control")

    /**
     * Characters allowed in a header name beyond letters and digits (RFC 9110 `token`).
     */
    private const val NAME_SYMBOLS = "!#$%&'*+-.^_`|~"

    /**
     * Whether [name] is an SDK-managed header that a consumer must not override.
     */
    fun isReserved(name: String): Boolean = name.trim().lowercase() in RESERVED

    /**
     * Whether [name] is a usable HTTP field-name — a non-empty RFC 9110 `token`. Leading and
     * trailing whitespace is tolerated (and stripped by [sanitize]); whitespace or a separator
     * inside the name is not, because no HTTP client can send it.
     */
    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.all { char ->
            char.isAsciiLetterOrDigit() || char in NAME_SYMBOLS
        }
    }

    /**
     * Whether [value] is a usable HTTP field-value — visible ASCII plus space and horizontal tab,
     * per RFC 9110 `field-value`. Surrounding whitespace is tolerated (and stripped by [sanitize]),
     * so a token read from a file or env var with a trailing newline still works.
     *
     * Anything else — CR, LF, NUL, control or non-ASCII characters — is rejected: HTTP clients
     * throw on it (which used to surface the value in an error message), and CR/LF inside a value
     * is the classic header-injection vector.
     */
    fun isValidValue(value: String): Boolean =
        value.trim().all { char -> char == '\t' || char.code in 0x20..0x7E }

    /**
     * Drops the entries the SDK cannot honour — [RESERVED] names, unusable names and unusable
     * values — and returns the rest with surrounding whitespace stripped.
     *
     * Silent by design: the SDK reports these problems once, up front, when the options are
     * validated at build time; this is the defence-in-depth pass on the request path, for options
     * assembled without going through that validation. Dropping here rather than letting the HTTP
     * client reject the value matters for privacy as much as for robustness — client exceptions
     * embed the offending value, and that message reaches the SDK's error log.
     */
    fun sanitize(headers: Map<String, String>?): Map<String, String> {
        if (headers.isNullOrEmpty()) return emptyMap()
        return buildMap {
            for ((name, value) in headers) {
                if (isReserved(name) || !isValidName(name) || !isValidValue(value)) continue
                put(name.trim(), value.trim())
            }
        }
    }

    /**
     * `Char.isLetterOrDigit()` is Unicode-aware; header names are ASCII-only.
     */
    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
