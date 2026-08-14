package com.sdk.growthbook

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.sandbox.GBCachingLayerAdapter
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBFeaturesChangeHandler

/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * ApiHost - domain for features fetch
 * StreamingHost - domain for server sent events
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 * enableLogging - Prints logging statements to stdout
 */
abstract class SDKBuilder(
    val apiKey: String,
    val apiHost: String,
    val streamingHost: String? = null,
    val attributes: Map<String, GBValue>,
    val trackingCallback: GBTrackingCallback,
    val encryptionKey: String?,
    val networkDispatcher: NetworkDispatcher,
    val remoteEval: Boolean,
    val enableLogging: Boolean,
) {
    internal var qaMode: Boolean = false
    internal var forcedVariations: Map<String, Int> = HashMap()
    internal var enabled: Boolean = true

    /**
     * Set Forced Variations - Default Empty
     */
    fun setForcedVariations(forcedVariations: Map<String, Int>): SDKBuilder {
        this.forcedVariations = forcedVariations
        return this
    }

    /**
     * Set QA Mode - Default Disabled
     */
    fun setQAMode(isEnabled: Boolean): SDKBuilder {
        this.qaMode = isEnabled
        return this
    }

    /**
     * If enabled - then experiments will be run,
     * otherwise default values will be returned.
     * Experiments are enabled by default.
     * If you want to disable experiments,
     * you can pass false here.
     */
    fun setEnabled(isEnabled: Boolean): SDKBuilder {
        this.enabled = isEnabled
        return this
    }

    /**
     * This method is open to be overridden by subclasses
     */
    abstract fun initialize(): GrowthBookSDK
}

/**
 * SDKBuilder - Initializer for GrowthBook SDK for Apps
 * APIKey - API Key
 * ApiHost - domain for features fetch
 * StreamingHost - domain for server sent events
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 * enableLogging - Prints logging statements to stdout
 */
class GBSDKBuilder(
    apiKey: String,
    apiHost: String,
    streamingHost: String? = null,
    networkDispatcher: NetworkDispatcher,
    attributes: Map<String, GBValue>,
    encryptionKey: String? = null,
    trackingCallback: GBTrackingCallback,
    remoteEval: Boolean = false,
    enableLogging: Boolean = false,
    private val cachingEnabled: Boolean = true,
) : SDKBuilder(
    apiKey, apiHost, streamingHost,
    attributes, trackingCallback, encryptionKey, networkDispatcher, remoteEval, enableLogging
) {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private var featuresChangeHandler: GBFeaturesChangeHandler? = null
    private var stickyBucketService: GBStickyBucketService? = null
    // Deferred builder for the default sticky-bucket service. The caching layer is resolved
    // lazily at initialize() time (via resolveCachingLayer()) rather than when the setter is
    // called, so setCachingLayer() and the sticky-bucket setters can be called in any order.
    private var stickyBucketServiceFactory: ((CachingLayer) -> GBStickyBucketService)? = null
    private var featureUsageCallback: GBFeatureUsageCallback? = null
    private var initialFeatures: GBFeatures? = null
    private var cacheMaxAge: Long? = null

    // Dispatcher used to process fetched payloads. Defaults to the platform IO dispatcher in
    // production; tests inject a deterministic dispatcher (e.g. Dispatchers.Unconfined or a
    // StandardTestDispatcher) to drive the async pipeline synchronously.
    private var coroutineContext: CoroutineContext = PlatformDependentIODispatcher
    private var customCachingLayer: GBCachingLayer? = null

    /**
     * Override the dispatcher used to process fetched payloads. Intended for tests that need
     * deterministic, synchronous application of mocked network responses.
     */
    internal fun setCoroutineContext(context: CoroutineContext): GBSDKBuilder {
        this.coroutineContext = context
        return this
    }

    /**
     * Set Refresh Handler - Will be called when cache is refreshed.
     *
     * Note: the handler is invoked from the SDK's payload-processing dispatcher (the platform IO
     * dispatcher by default), i.e. on a background thread — not necessarily the main thread.
     * Marshal back to your UI thread yourself if the callback touches UI state.
     */
    fun setRefreshHandler(refreshHandler: GBCacheRefreshHandler): GBSDKBuilder {
        this.refreshHandler = refreshHandler
        return this
    }

    fun setFeaturesChangeHandler(featuresChangeHandler: GBFeaturesChangeHandler): GBSDKBuilder {
        this.featuresChangeHandler = featuresChangeHandler
        return this
    }

    /**
     * Seed the SDK with a bundled fallback payload (e.g. snapshotted at build time).
     * Features are applied immediately so flags are available from the first millisecond,
     * and the normal cache/network refresh still runs on top — overwriting the seed as
     * fresher data arrives. Effective precedence: network > disk cache > seed > code defaults.
     */
    fun setInitialFeatures(features: GBFeatures): GBSDKBuilder {
        this.initialFeatures = features
        return this
    }

    /**
    * Method for enable  default sticky bucket service
    */
    fun setStickyBucketService(coroutineScope: CoroutineScope): GBSDKBuilder {
        this.stickyBucketService = null
        this.stickyBucketServiceFactory = { localStorage ->
            GBStickyBucketServiceImp(
                coroutineScope = coroutineScope,
                prefix = "gbStickyBuckets__${apiKey}_",
                localStorage = localStorage,
            )
        }
        return this
    }

    /**
     * Method for enable sticky bucket service
     */
    fun setStickyBucketService(
        stickyBucketService: GBStickyBucketService
    ): GBSDKBuilder {
        this.stickyBucketService = stickyBucketService
        this.stickyBucketServiceFactory = null
        return this
    }

    /**
     * Method for set prefix of filename in cache directory GrowthBook-KMM.
     * Structure of filename - prefix$attributeName||$attributeValue
     * Default prefix of filename `gbStickyBuckets__`
     * Example name of file be like `gbStickyBuckets__test||testAttribute.txt`
     */
    fun setPrefixForStickyBucketCachedDirectory(
        coroutineScope: CoroutineScope,
        prefix: String = "gbStickyBuckets__${apiKey}_"
    ): GBSDKBuilder {
        this.stickyBucketService = null
        this.stickyBucketServiceFactory = { localStorage ->
            GBStickyBucketServiceImp(coroutineScope, prefix, localStorage)
        }
        return this
    }

    /**
     * Setter for featureUsageCallback.
     * A callback that will be invoked every time a feature is viewed.
     */
    fun setFeatureUsageCallback(featureUsageCallback: GBFeatureUsageCallback): GBSDKBuilder {
        this.featureUsageCallback = featureUsageCallback
        return this
    }

    /**
     * Sets the freshness window for cached features.
     *
     * While the cache is younger than this age, the network call on the next
     * fetch is skipped and the cached features are served as the authoritative
     * result. Once the cache is older, the SDK refetches from the network.
     * This is a cache-staleness gate evaluated on the next fetch, not a
     * background polling mechanism. When unset, the SDK always refetches.
     * To force a network refresh regardless of this window, call
     * [GrowthBookSDK.refreshCache].
     *
     * @param cacheMaxAge freshness window in milliseconds.
     */
    fun setCacheMaxAge(cacheMaxAge: Long): GBSDKBuilder {
        this.cacheMaxAge = cacheMaxAge
        return this
    }

    /**
     * Provide a custom cache implementation, replacing the built-in per-platform cache.
     * Replaces the feature-definition cache and also routes sticky-bucket storage through it.
     * May be called in any order relative to the sticky-bucket setters.
     */
    fun setCachingLayer(cachingLayer: GBCachingLayer): GBSDKBuilder {
        this.customCachingLayer = cachingLayer
        return this
    }

    /**
     * Initialize the Kotlin SDK and provide it when ready
     */
    fun initialize(onResult: (GrowthBookSDK) -> Unit) {
        val gbContext = createGbContext()

        WaitForCallCaseHelper(
            gbContext = gbContext,
            onResult = onResult,
        )
    }

    /**
     * Initialize the Kotlin SDK
     * This init method takes less time than method above
     */
    override fun initialize(): GrowthBookSDK {
        val gbContext = createGbContext()

        if (enableLogging && !cachingEnabled) {
            GB.warning(
                """
                    calling #initialize with caching
                    disabled will cause feature values nulls. We recommend to enable
                    caching or calling method #initialize with callback
                """.trimIndent()
            )
        }

        initialFeatures?.let { gbContext.features = it }

        val gbOptions = GBOptions(apiHost, streamingHost)

        return GrowthBookSDK(
            gbContext,
            gbOptions,
            refreshHandler,
            networkDispatcher,
            cachingEnabled = cachingEnabled,
            cacheMaxAge = cacheMaxAge,
            coroutineContext = coroutineContext,
            featuresChangeHandler = featuresChangeHandler,
            cachingLayer = customCachingLayer
        )
    }

    private fun createGbContext() =
        GBContext(
            apiKey = apiKey,
            enabled = enabled,
            attributes = attributes,
            qaMode = qaMode,
            forcedVariations = forcedVariations,
            trackingCallback = trackingCallback,
            onFeatureUsage = featureUsageCallback,
            encryptionKey = encryptionKey,
            remoteEval = remoteEval,
            enableLogging = enableLogging,
            // Resolve the caching layer now, so a custom layer set via setCachingLayer() is
            // honoured regardless of whether it was set before or after the sticky-bucket setter.
            stickyBucketService = stickyBucketService
                ?: stickyBucketServiceFactory?.invoke(resolveCachingLayer()),
        )

    private inner class WaitForCallCaseHelper(
        gbContext: GBContext,
        private val onResult: (GrowthBookSDK) -> Unit
    ) {
        var growthBookSDK: GrowthBookSDK? = null
        private var handleWaitForCallCallback: (() -> Unit)? = {
            growthBookSDK?.let(onResult)
        }

        init {
            val internalRefreshHandler: GBCacheRefreshHandler = { arg1, arg2 ->
                refreshHandler?.invoke(arg1, arg2)

                if (arg2 != null && enableLogging) {
                    GB.warning(
                        "GrowthBook error: " + arg2.errorMessage
                    )
                }

                // it can be called only one time
                // a continuation represents a single suspension point
                handleWaitForCallCallback?.invoke()
                handleWaitForCallCallback = null
                growthBookSDK = null
            }
            initialFeatures?.let { gbContext.features = it }

            val gbOptions = GBOptions(apiHost, streamingHost)
            growthBookSDK = GrowthBookSDK(
                gbContext,
                gbOptions,
                internalRefreshHandler,
                networkDispatcher,
                cachingEnabled = cachingEnabled,
                cacheMaxAge = cacheMaxAge,
                coroutineContext = coroutineContext,
                featuresChangeHandler = featuresChangeHandler,
                cachingLayer = customCachingLayer
            )
        }
    }

    private fun resolveCachingLayer(): CachingLayer =
        customCachingLayer?.let { GBCachingLayerAdapter(it) } ?: CachingImpl.getLayer()
}