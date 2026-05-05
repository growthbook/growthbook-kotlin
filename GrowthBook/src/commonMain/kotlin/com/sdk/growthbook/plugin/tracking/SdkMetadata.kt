package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.model.SDK_VERSION

/**
 * Identifying metadata for this SDK. Emitted on every tracked event and in
 * the {@code User-Agent} of outbound ingest requests.
 */
internal object SdkMetadata {
    const val LANGUAGE = "Kotlin"
    const val NAME = "growthbook-kotlin-sdk"
    const val VERSION: String = SDK_VERSION
    const val USER_AGENT = "$NAME/$VERSION"
}
