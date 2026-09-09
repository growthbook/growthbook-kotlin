package com.sdk.growthbook

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.EvalSnapshot
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.features.DecodedPayload
import com.sdk.growthbook.features.FeaturePayloadDecoder
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.sandbox.GBCachingLayerAdapter
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBFeaturesChangeHandler
import com.sdk.growthbook.utils.GBFetchStatsHandler
import com.sdk.growthbook.utils.GBOptionsValidator

/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * ApiHost - domain for features fetch
 * StreamingHost - domain for server sent events; falls back to ApiHost when not set
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
 * StreamingHost - domain for server sent events; falls back to ApiHost when not set
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
    private var fetchStatsHandler: GBFetchStatsHandler? = null
    private var stickyBucketService: GBStickyBucketService? = null
    // Deferred builder for the default sticky-bucket service. The caching layer is resolved
    // lazily at initialize() time (via resolveCachingLayer()) rather than when the setter is
    // called, so setCachingLayer() and the sticky-bucket setters can be called in any order.
    private var stickyBucketServiceFactory: ((CachingLayer) -> GBStickyBucketService)? = null
    private var featureUsageCallback: GBFeatureUsageCallback? = null
    private var plugins: List<GrowthBookPlugin>? = null
    private var initialFeatures: GBFeatures? = null
    private var initialPayloadJson: String? = null
    private var cacheMaxAge: Long? = null
    private var refreshInterval: Long? = null
    private var staleTtl: Long? = null
    private var serveStaleOnError: Boolean = false
    private var apiHostRequestHeaders: Map<String, String> = emptyMap()
    private var streamingHostRequestHeaders: Map<String, String> = emptyMap()

    // Dispatcher used to process fetched payloads. Defaults to the platform IO dispatcher in
    // production; tests inject a deterministic dispatcher (e.g. Dispatchers.Unconfined or a
    // StandardTestDispatcher) to drive the async pipeline synchronously.
    private var coroutineContext: CoroutineContext = PlatformDependentIODispatcher
    private var customCachingLayer: GBCachingLayer? = null

    // Matches the parser the network path uses, so a bundled snapshot is accepted on exactly the
    // same terms as a live response (unknown//future payload fields ignored rather than fatal).
    private val seedJsonParser = Json { isLenient = true; ignoreUnknownKeys = true }

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
     * Set Fetch Stats Handler - Will be called once per feature fetch with how long it took and
     * how large the payload was. Use it to measure what users actually experience on first
     * launch; the edge completes a response before the device has received it, so fetch duration
     * cannot be measured server-side.
     *
     * Invoked on the network callback's thread, before the payload is parsed.
     *
     * Scope: feature GET fetches only. Remote evaluation (`remoteEval = true`) goes through a
     * POST that is not reported — a server-side evaluation and a CDN GET have very different
     * latency profiles, so folding them into one stream would corrupt both averages. Reporting
     * for remote eval (with a source discriminator) is a possible follow-up.
     */
    fun setFetchStatsHandler(fetchStatsHandler: GBFetchStatsHandler): GBSDKBuilder {
        this.fetchStatsHandler = fetchStatsHandler
        return this
    }

    /**
     * Seed the SDK with a bundled fallback payload (e.g. snapshotted at build time).
     * Features are applied immediately so flags are available from the first millisecond,
     * and the normal cache/network refresh still runs on top — overwriting the seed as
     * fresher data arrives. Effective precedence: network > disk cache > seed > code defaults.
     *
     * Takes an already-decoded feature map; see [setInitialPayload] to seed straight from a
     * payload as the API returns it.
     */
    fun setInitialFeatures(features: GBFeatures): GBSDKBuilder {
        this.initialFeatures = features
        return this
    }

    /**
     * Seed the SDK with a bundled fallback payload in its raw API form — the exact JSON body the
     * features endpoint returns, e.g. snapshotted into your assets at build time.
     *
     * Use this instead of [setInitialFeatures] when the payload carries more than features:
     * `savedGroups` and `contextualBandits` are seeded too, and the encrypted variants
     * (`encryptedFeatures` / `encryptedSavedGroups` / `encryptedContextualBandits`) are decrypted
     * with the builder's encryption key — so an encrypted snapshot can be bundled without
     * decrypting it at build time and shipping plaintext definitions inside the app. Contextual
     * bandit rules in particular are inert without their definitions, so a bundled payload for a
     * bandit-driven feature must go through here.
     *
     * Like [setInitialFeatures], this is only a seed: the normal cache/network refresh still runs on
     * top and overwrites it as fresher data arrives (network > disk cache > seed > code defaults).
     * A payload that cannot be parsed or decrypted is ignored (logged when logging is enabled)
     * rather than failing initialization — the seed is a fallback, not a hard dependency.
     *
     * If both this and [setInitialFeatures] are set, the explicit features win over the payload's.
     */
    fun setInitialPayload(json: String): GBSDKBuilder {
        this.initialPayloadJson = json
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
     * result. Once the cache is older, the SDK refetches from the network — the
     * stale cache is still served (as a non-authoritative result) while that
     * refresh runs, it is never dropped. This is a cache-staleness gate evaluated
     * on the next fetch, not a background polling mechanism. When unset, the SDK
     * always refetches. To force a network refresh regardless of this window,
     * call [GrowthBookSDK.refreshCache].
     *
     * Pair this with [setStaleTtl] when you also need a hard staleness ceiling
     * (past which the cache is no longer served at all); used alone, this window
     * has no such cutoff.
     *
     * No effect when the SDK is built with `remoteEval = true`: a remote-eval
     * payload is evaluated server-side against the current attributes while the
     * cache is keyed only by API key, so that mode bypasses the feature cache
     * entirely (nothing is read from it, nothing written to it) and always hits
     * the network.
     *
     * @param cacheMaxAge freshness window in milliseconds.
     * @throws IllegalArgumentException if [cacheMaxAge] is not positive. A non-positive
     *   window makes every cache entry stale, which is indistinguishable from not
     *   setting it at all — omit the setter instead. (Behaviour change in 7.9.0: this
     *   was previously accepted and silently disabled the freshness window.)
     * @see setStaleTtl
     * @see setServeStaleOnError
     */
    fun setCacheMaxAge(cacheMaxAge: Long): GBSDKBuilder {
        require(cacheMaxAge > 0) { "cacheMaxAge must be positive, was $cacheMaxAge" }
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
     * Extra headers added to every request against the API host — the features `GET` and the
     * remote-evaluation `POST`. Use this to reach a GrowthBook instance deployed behind an
     * authenticated gateway or proxy.
     *
     * ```kotlin
     * .setApiHostRequestHeaders(mapOf("Authorization" to "Bearer $gatewayToken"))
     * ```
     *
     * Header values may contain credentials: they are never logged and never surfaced through
     * diagnostics. Whether they are actually applied depends on the injected
     * [NetworkDispatcher] — both built-in dispatchers (Ktor and OkHttp) honour them; a custom
     * dispatcher must override the `headers`-carrying overloads of [NetworkDispatcher] to do so.
     *
     * @throws com.sdk.growthbook.utils.GBInvalidOptionsException when a header name is blank, is
     *   not a valid HTTP token, is one of the SDK-managed names (`If-None-Match`, `Cache-Control`
     *   — the SDK sets those itself and honouring an override would break ETag-based
     *   revalidation), or when a value contains characters HTTP forbids (control characters, line
     *   breaks, non-ASCII). The check runs here so the misconfiguration surfaces at the setter,
     *   not at the first failed fetch. Violation messages never quote a header value.
     */
    fun setApiHostRequestHeaders(headers: Map<String, String>): GBSDKBuilder {
        GBOptionsValidator.validate(
            streamingHost = null,
            apiHostRequestHeaders = headers,
            streamingHostRequestHeaders = null,
        )
        this.apiHostRequestHeaders = headers.toMap()
        return this
    }

    /**
     * Extra headers added to the SSE streaming request (against the streaming host, or the API
     * host when no streaming host is configured). Same reserved-name and secret-handling rules as
     * [setApiHostRequestHeaders].
     *
     * @throws com.sdk.growthbook.utils.GBInvalidOptionsException when a header name is blank,
     *   reserved or not a valid HTTP token, or when a value contains characters HTTP forbids.
     */
    fun setStreamingHostRequestHeaders(headers: Map<String, String>): GBSDKBuilder {
        GBOptionsValidator.validate(
            streamingHost = null,
            apiHostRequestHeaders = null,
            streamingHostRequestHeaders = headers,
        )
        this.streamingHostRequestHeaders = headers.toMap()
        return this
    }

    /**
     * Registers plugins that receive lifecycle callbacks: [GrowthBookPlugin.init],
     * [GrowthBookPlugin.onExperimentViewed], [GrowthBookPlugin.onFeatureEvaluated], and [GrowthBookPlugin.close].
     */
    fun setPlugins(plugins: List<GrowthBookPlugin>): GBSDKBuilder {
        this.plugins = plugins
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
     *
     * Scope: this is a safety net for *automatic* refreshes (startup, background polling, the
     * stale-while-revalidate round). It does not apply to an explicit
     * [GrowthBookSDK.refreshCache], which reports the network failure through
     * [GBCacheRefreshHandler] instead of quietly applying an expired payload — that refresh is
     * coalesced with any in-flight round, and a per-caller fallback cannot be attributed once
     * several callers share it.
     *
     * No effect when the SDK is built with `remoteEval = true`: that mode bypasses the feature cache
     * entirely, so there is no expired entry to fall back to.
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
     *
     * No effect when the SDK is built with `remoteEval = true`: that mode bypasses the feature cache
     * entirely, so there is no cached entry to classify.
     *
     * @throws IllegalArgumentException if [ttlMs] is not positive. A non-positive window would leave
     *   no "fresh" zone at all (every cache entry classified stale), which is never what a caller
     *   means — omit the setter instead.
     */
    fun setStaleTtl(ttlMs: Long): GBSDKBuilder {
        require(ttlMs > 0) { "staleTtl must be positive, was $ttlMs" }
        this.staleTtl = ttlMs
        return this
    }

    /**
     * Initialize the Kotlin SDK and provide it when ready
     */
    fun initialize(onResult: (GrowthBookSDK) -> Unit) {
        // Validated first, before any context/service is built, so a misconfiguration throws
        // without leaving half-initialized state behind.
        val gbOptions = createGbOptions()
        val gbContext = createGbContext()

        WaitForCallCaseHelper(
            gbContext = gbContext,
            gbOptions = gbOptions,
            onResult = onResult,
        )
    }

    /**
     * Initialize the Kotlin SDK
     * This init method takes less time than method above
     */
    override fun initialize(): GrowthBookSDK {
        // Validated first, before any context/service is built, so a misconfiguration throws
        // without leaving half-initialized state behind.
        val gbOptions = createGbOptions()
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

        seedInitialState(gbContext)

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
            featuresChangeHandler = featuresChangeHandler,
            cachingLayer = customCachingLayer,
            fetchStatsHandler = fetchStatsHandler
        )
    }

    /**
     * Assembles (and validates) the host/header options. The hosts are validated here rather than
     * in the constructor so an already-compiled consumer passing a malformed value still gets the
     * same fail-fast error at `initialize()` regardless of which builder overload it uses.
     * `apiHost` is checked too — it backs every feature fetch and remote-eval POST, so a typo
     * there degrades into exactly the same opaque fetch failure as a bad `streamingHost`.
     */
    private fun createGbOptions(): GBOptions {
        GBOptionsValidator.validate(
            streamingHost = streamingHost,
            apiHostRequestHeaders = apiHostRequestHeaders,
            streamingHostRequestHeaders = streamingHostRequestHeaders,
            apiHost = apiHost,
        )
        return GBOptions(
            apiHost = apiHost,
            streamingHost = streamingHost,
            apiHostRequestHeaders = apiHostRequestHeaders,
            streamingHostRequestHeaders = streamingHostRequestHeaders,
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
            plugins = plugins,
        )

    private inner class WaitForCallCaseHelper(
        gbContext: GBContext,
        gbOptions: GBOptions,
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
            seedInitialState(gbContext)

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
                featuresChangeHandler = featuresChangeHandler,
                cachingLayer = customCachingLayer,
                fetchStatsHandler = fetchStatsHandler
            )
        }
    }

    /**
     * Applies the bundled seed to [gbContext] before the SDK starts its own fetch: first the raw
     * payload from [setInitialPayload] (decrypted if needed), then the explicit features from
     * [setInitialFeatures], which therefore take precedence. A seed that fails to parse is skipped —
     * the SDK then simply starts empty and waits for cache/network, as it would without a seed.
     */
    private fun seedInitialState(gbContext: GBContext) {
        initialPayloadJson?.let { json ->
            val decoded = runCatching {
                val model = seedJsonParser
                    .decodeFromString(SerializableFeaturesDataModel.serializer(), json)
                    .gbDeserialize()
                FeaturePayloadDecoder(encryptionKey).decode(model)
            }.getOrElse { error ->
                if (enableLogging) {
                    GB.error("GBSDKBuilder: setInitialPayload could not be parsed, ignoring seed", error)
                }
                null
            }

            decoded?.let {
                gbContext.applyPayload(
                    features = it.features,
                    savedGroups = it.savedGroups?.mapValues { (_, value) -> GBValue.from(value) },
                    contextualBandits = it.contextualBandits,
                )
            }
        }
        initialFeatures?.let { gbContext.features = it }
    }

    private fun resolveCachingLayer(): CachingLayer =
        customCachingLayer?.let { GBCachingLayerAdapter(it) } ?: CachingImpl.getLayer()
}
