package com.sdk.growthbook.model

/**
 * Host and per-host request-header configuration for the feature/streaming endpoints.
 *
 * SDK-owned: built by [com.sdk.growthbook.GBSDKBuilder], never by consumers. The constructor is
 * internal (and `copy()` with it) so new options can be added without breaking already-compiled
 * consumers — see the API-stability note in CLAUDE.md.
 *
 * @param apiHost domain features are fetched from (and remote evaluation is POSTed to).
 * @param streamingHost dedicated domain for server-sent events. When null or blank, streaming
 *   falls back to [apiHost] — matching the TypeScript SDK. Set it to use GrowthBook Cloud's
 *   dedicated streaming domain, or a separate streaming endpoint in a self-hosted deployment.
 * @param apiHostRequestHeaders extra headers added to every request against [apiHost] — the
 *   features `GET` and the remote-evaluation `POST`. Use them to authenticate against a gateway or
 *   proxy that fronts a self-hosted GrowthBook (`mapOf("Authorization" to "Bearer …")`).
 *   SDK-managed headers ([com.sdk.growthbook.network.GBRequestHeaders.RESERVED]: `If-None-Match`,
 *   `Cache-Control`) cannot be overridden — supplying one is rejected by
 *   [com.sdk.growthbook.GBSDKBuilder.setApiHostRequestHeaders] and dropped again on the request
 *   path, as are names and values no HTTP client can send.
 * @param streamingHostRequestHeaders extra headers added to the SSE streaming request (against
 *   [streamingHost], or [apiHost] when no streaming host is set). Same reserved-name and
 *   secret-handling rules as [apiHostRequestHeaders]; prefer
 *   [com.sdk.growthbook.GBSDKBuilder.setStreamingHostRequestHeaders], which validates the map up
 *   front.
 */
@ConsistentCopyVisibility
data class GBOptions internal constructor(
    val apiHost: String,
    val streamingHost: String?,
    val apiHostRequestHeaders: Map<String, String> = emptyMap(),
    val streamingHostRequestHeaders: Map<String, String> = emptyMap(),
) {

    /**
     * Header *values* may carry credentials (a gateway token, a signed cookie), so the generated
     * `toString()` is replaced with one that lists only the names — a debug dump of these options
     * must not leak a secret. Names alone are safe to log; see
     * [com.sdk.growthbook.network.GBRequestHeaders].
     */
    override fun toString(): String =
        "GBOptions(apiHost=$apiHost, streamingHost=$streamingHost, " +
            "apiHostRequestHeaders=${apiHostRequestHeaders.keys}, " +
            "streamingHostRequestHeaders=${streamingHostRequestHeaders.keys})"
}
