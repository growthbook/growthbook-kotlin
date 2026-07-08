package com.sdk.growthbook.ext

import com.sdk.growthbook.GBFeatureUsageCallback
import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBFeatures

/**
 * Marks the receiver scope of the configuration DSL so an inner receiver scope
 * (e.g. the nested [GrowthBookConfigBuilder.attributes] block) cannot implicitly
 * access the outer [GrowthBookConfigBuilder]'s members.
 */
@DslMarker
annotation class GBConfigDsl

/**
 * Builder scope for the GrowthBook configuration DSL.
 *
 * Mirrors the fields of [GBSDKBuilder] behind plain properties so the SDK can be
 * assembled declaratively — hiding the constructor-vs-setter split of the
 * underlying builder. Collected state is turned into a live [GrowthBookSDK] by
 * [build], which the [growthBook] entry point calls for you.
 *
 * [apiKey], [apiHost] and [networkDispatcher] are **required**: omitting any of
 * them makes [build] throw [IllegalArgumentException]. Every other property is
 * optional and falls back to the SDK default.
 *
 * Prefer the top-level [growthBook] function over constructing this directly.
 */
@GBConfigDsl
class GrowthBookConfigBuilder {

    /** API key of the GrowthBook environment. **Required.** */
    var apiKey: String? = null

    /** Host that serves feature definitions. **Required.** */
    var apiHost: String? = null

    /**
     * Dispatcher used for fetching and streaming features. **Required** — supply
     * an implementation from the `NetworkDispatcherKtor` (e.g.
     * `GBNetworkDispatcherKtor`) or `NetworkDispatcherOkHttp` module; this module
     * intentionally ships none.
     */
    var networkDispatcher: NetworkDispatcher? = null

    /** Optional host for server-sent events (live streaming updates). */
    var streamingHost: String? = null

    /** Optional key for decrypting encrypted feature payloads. */
    var encryptionKey: String? = null

    /** When true, the SDK prints logging statements to stdout. Defaults to false. */
    var enableLogging: Boolean = false

    /** When true, features are evaluated remotely instead of locally. Defaults to false. */
    var remoteEval: Boolean = false

    /** When true, disables random assignment for deterministic QA runs. Defaults to false. */
    var qaMode: Boolean = false

    /** When false, experiments are not run and default values are returned. Defaults to true. */
    var enabled: Boolean = true

    /** Map of experiment key to forced variation index. Defaults to empty. */
    var forceVariations: Map<String, Int> = emptyMap()

    /** Invoked whenever a user is included in an experiment. Defaults to a no-op. */
    var trackingCallback: GBTrackingCallback = { _, _ -> }

    /** Optional callback invoked when the feature cache is refreshed. */
    var refreshHandler: GBCacheRefreshHandler? = null

    /** Optional callback invoked every time a feature is evaluated. */
    var featureUsageCallback: GBFeatureUsageCallback? = null

    /** Optional sticky-bucket service for persisting variation assignments. */
    var stickyBucketService: GBStickyBucketService? = null

    /**
     * Optional bundled features applied immediately, before any network fetch —
     * an offline seed so flags are available from the first millisecond. The
     * normal cache/network refresh still runs on top.
     */
    var initialFeatures: GBFeatures? = null

    private var attributes: Map<String, GBValue> = emptyMap()

    /**
     * Sets the user attributes used for targeting and experiment assignment,
     * using the attributes DSL (see [buildAttributes]).
     *
     * ```kotlin
     * attributes {
     *     "id" to "user-123"
     *     "country" to "UA"
     * }
     * ```
     */
    fun attributes(block: GBAttributesBuilder.() -> Unit) {
        attributes = buildAttributes(block)
    }

    /**
     * Validates the required properties and maps the collected configuration onto
     * a [GBSDKBuilder], returning an initialized [GrowthBookSDK]. Optional fields
     * are applied only when set, so unset values keep the SDK defaults.
     *
     * @throws IllegalArgumentException if [apiKey], [apiHost] or
     * [networkDispatcher] is missing
     */
    internal fun build(): GrowthBookSDK {
        val key = requireNotNull(apiKey) {
            "growthBook { apiKey = ... } is required"
        }
        val host = requireNotNull(apiHost) {
            "growthBook { apiHost = ... } is required"
        }
        val dispatcher = requireNotNull(networkDispatcher) {
            "growthBook { networkDispatcher = ... } is required — provide one from " +
                "NetworkDispatcherKtor or NetworkDispatcherOkHttp"
        }

        val builder = GBSDKBuilder(
            apiKey = key,
            apiHost = host,
            networkDispatcher = dispatcher,
            attributes = attributes,
            trackingCallback = trackingCallback,
            streamingHost = streamingHost,
            encryptionKey = encryptionKey,
            remoteEval = remoteEval,
            enableLogging = enableLogging
        )
        builder.setForcedVariations(forceVariations)
        builder.setEnabled(enabled)
        builder.setQAMode(qaMode)
        initialFeatures?.let { builder.setInitialFeatures(it) }
        refreshHandler?.let { builder.setRefreshHandler(it) }
        featureUsageCallback?.let { builder.setFeatureUsageCallback(it) }
        stickyBucketService?.let { builder.setStickyBucketService(it) }

        return builder.initialize()
    }
}

/**
 * Entry point for the configuration DSL: builds and initializes a
 * [GrowthBookSDK] declaratively, hiding the [GBSDKBuilder] wiring.
 *
 * ```kotlin
 * val sdk = growthBook {
 *     apiKey = "sdk-abc"
 *     apiHost = "https://cdn.growthbook.io"
 *     networkDispatcher = GBNetworkDispatcherKtor()   // from the NetworkDispatcherKtor module
 *
 *     enableLogging = true
 *     attributes {
 *         "id" to "user-123"
 *         "premium" to true
 *     }
 * }
 * ```
 *
 * @throws IllegalArgumentException if any required field ([GrowthBookConfigBuilder.apiKey],
 * [GrowthBookConfigBuilder.apiHost], [GrowthBookConfigBuilder.networkDispatcher]) is missing
 */
fun growthBook(block: GrowthBookConfigBuilder.() -> Unit): GrowthBookSDK =
    GrowthBookConfigBuilder().apply(block).build()
