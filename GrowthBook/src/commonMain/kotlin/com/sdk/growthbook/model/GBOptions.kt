package com.sdk.growthbook.model

/**
 * SDK-owned: built by [com.sdk.growthbook.GBSDKBuilder], never by consumers. The constructor is
 * internal (and `copy()` with it) so new options can be added without breaking already-compiled
 * consumers — see the API-stability note in CLAUDE.md.
 */
@ConsistentCopyVisibility
data class GBOptions internal constructor(
    val apiHost: String,
    val streamingHost: String?,
)
