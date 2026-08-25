package com.sdk.growthbook.ext

import com.sdk.growthbook.GBFeatureUsageCallback
import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBFeaturesChangeHandler
import kotlinx.coroutines.CoroutineScope

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

    /** Optional callback invoked when the set of feature definitions changes. */
    var featuresChangeHandler: GBFeaturesChangeHandler? = null

    /**
     * Plugins receiving lifecycle callbacks (init / experiment viewed / feature evaluated /
     * close) — for example the built-in tracking plugin. Defaults to none.
     */
    var plugins: List<GrowthBookPlugin> = emptyList()

    /**
     * When false, the SDK neither reads nor writes the feature cache. Defaults to true.
     * Useful for tests and for hosts that must not touch the filesystem / local storage.
     */
    var cachingEnabled: Boolean = true

    /**
     * Optional freshness window for cached features, in milliseconds. While the cache is
     * younger than this, the next fetch is skipped and the cached payload is served. When
     * unset, the SDK always refetches.
     */
    var cacheMaxAge: Long? = null

    /**
     * Optional custom cache, replacing the built-in per-platform one. Also backs
     * sticky-bucket storage; may be combined with the sticky-bucket options in any order.
     */
    var cachingLayer: GBCachingLayer? = null

    /** Optional sticky-bucket service for persisting variation assignments. */
    var stickyBucketService: GBStickyBucketService? = null

    /**
     * Enables the built-in sticky-bucket service on this scope, instead of supplying your own
     * via [stickyBucketService] (which takes precedence when both are set).
     */
    var stickyBucketScope: CoroutineScope? = null

    /**
     * Optional filename prefix for the built-in sticky-bucket store; requires
     * [stickyBucketScope]. Defaults to `gbStickyBuckets__<apiKey>_`.
     */
    var stickyBucketPrefix: String? = null

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
            enableLogging = enableLogging,
            cachingEnabled = cachingEnabled
        )
        builder.setForcedVariations(forceVariations)
        builder.setEnabled(enabled)
        builder.setQAMode(qaMode)
        initialFeatures?.let { builder.setInitialFeatures(it) }
        refreshHandler?.let { builder.setRefreshHandler(it) }
        featuresChangeHandler?.let { builder.setFeaturesChangeHandler(it) }
        featureUsageCallback?.let { builder.setFeatureUsageCallback(it) }
        cacheMaxAge?.let { builder.setCacheMaxAge(it) }
        cachingLayer?.let { builder.setCachingLayer(it) }
        if (plugins.isNotEmpty()) {
            builder.setPlugins(plugins)
        }
        // An explicit service wins over the built-in one; the prefix is only meaningful for the
        // built-in service, which is why it is applied through the scope-based setter.
        val service = stickyBucketService
        val scope = stickyBucketScope
        when {
            service != null -> builder.setStickyBucketService(service)
            scope != null -> stickyBucketPrefix
                ?.let { builder.setPrefixForStickyBucketCachedDirectory(scope, it) }
                ?: builder.setStickyBucketService(scope)
        }

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
