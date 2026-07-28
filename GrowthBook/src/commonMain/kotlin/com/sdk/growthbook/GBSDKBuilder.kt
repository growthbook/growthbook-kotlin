package com.sdk.growthbook

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBFeatures

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
    private var stickyBucketService: GBStickyBucketService? = null
    private var featureUsageCallback: GBFeatureUsageCallback? = null
    private var initialFeatures: GBFeatures? = null
    private var cacheMaxAge: Long? = null
    private var refreshInterval: Long? = null
    private var staleTtl: Long? = null
    private var serveStaleOnError: Boolean = false

    // Dispatcher used to process fetched payloads. Defaults to the platform IO dispatcher in
    // production; tests inject a deterministic dispatcher (e.g. Dispatchers.Unconfined or a
    // StandardTestDispatcher) to drive the async pipeline synchronously.
    private var coroutineContext: CoroutineContext = PlatformDependentIODispatcher

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
        return setStickyBucketService(
            GBStickyBucketServiceImp(
                coroutineScope = coroutineScope,
                prefix = "gbStickyBuckets__${apiKey}_",
                localStorage = CachingImpl.getLayer(),
            )
        )
    }

    /**
     * Method for enable sticky bucket service
     */
    fun setStickyBucketService(
        stickyBucketService: GBStickyBucketService
    ): GBSDKBuilder {
        this.stickyBucketService = stickyBucketService
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
        this.stickyBucketService = GBStickyBucketServiceImp(
            coroutineScope, prefix, CachingImpl.getLayer()
        )
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
     * Opt-in background polling interval in milliseconds.
     *
     * When set, [GrowthBookSDK.startPolling] launches a coroutine on the SDK's background scope
     * that revalidates features from the network every [intervalMs]. It is a suspend loop, not a
     * dedicated thread, so it is cheap while idle. Mutually exclusive with SSE auto-refresh, and SSE
     * wins: starting SSE stops any running poller, while [GrowthBookSDK.startPolling] is a no-op while
     * SSE is active. Disabled (null) by default.
     *
     * Mobile note: the SDK cannot observe app lifecycle, so tie [GrowthBookSDK.startPolling] /
     * [GrowthBookSDK.stopPolling] to your foreground/background transitions to avoid draining the
     * radio in the background. On mobile prefer SSE or the pull-on-access cache window
     * ([setCacheMaxAge]); polling is intended mainly for long-lived JVM/backend usage.
     */
    fun setRefreshInterval(intervalMs: Long): GBSDKBuilder {
        require(intervalMs > 0) { "refreshInterval must be positive, was $intervalMs" }
        this.refreshInterval = intervalMs
        return this
    }

    /**
     * When true, an expired cache (older than [setCacheMaxAge], with [setStaleTtl] set) is served as
     * a last-resort fallback if the revalidating network round fails — HTTP `stale-if-error`
     * semantics, useful for offline resilience on mobile. Default false fails closed: past the
     * ceiling nothing stale is served and the SDK falls back to code defaults. Only has an effect
     * together with [setStaleTtl] + [setCacheMaxAge].
     *
     * Observability note: when the stale fallback is served after a failed refresh, it is applied as a
     * non-authoritative payload and the refresh handler ([GBCacheRefreshHandler]) is **not** invoked —
     * neither as success nor as failure. The handler's `(Boolean, GBError?)` contract cannot express
     * "stale fallback served", so signalling either would mislead; treat `serveStaleOnError` as a
     * best-effort offline safety net rather than a signal you can observe through the handler.
     */
    fun setServeStaleOnError(enabled: Boolean): GBSDKBuilder {
        this.serveStaleOnError = enabled
        return this
    }

    /**
     * Inner "fresh" window (ms) that turns [setCacheMaxAge] into a full three-tier
     * stale-while-revalidate policy:
     *  - age < staleTtl                -> fresh: served from cache, network skipped
     *  - staleTtl <= age < cacheMaxAge -> stale: served immediately while a background refresh runs
     *  - age >= cacheMaxAge            -> expired: NOT served, refetched from the network (cache miss)
     *
     * Use `staleTtl` when you need both a low revalidation cadence AND a hard staleness ceiling
     * (e.g. "revalidate at most once a minute, but never serve data older than 24h"). Pair it with
     * [setCacheMaxAge] as the outer ceiling; when both are set `ttlMs` must be `< cacheMaxAge`
     * (enforced at construction). Set on its own (without [setCacheMaxAge]) `staleTtl` is just the
     * inner "fresh" window with no hard cutoff — a cache past it is served while revalidating.
     * When `staleTtl` is unset, [setCacheMaxAge] alone governs the skip-network window with NO hard
     * cutoff (it keeps serving stale beyond the window while revalidating) — the pre-existing
     * behaviour.
     */
    fun setStaleTtl(ttlMs: Long): GBSDKBuilder {
        this.staleTtl = ttlMs
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
            refreshInterval = refreshInterval,
            staleTtl = staleTtl,
            serveStaleOnError = serveStaleOnError,
            coroutineContext = coroutineContext,
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
            stickyBucketService = stickyBucketService,
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
                refreshInterval = refreshInterval,
                staleTtl = staleTtl,
                serveStaleOnError = serveStaleOnError,
                coroutineContext = coroutineContext,
            )
        }
    }
}