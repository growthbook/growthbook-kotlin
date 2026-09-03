package com.sdk.growthbook

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.BackoffPolicy
import com.sdk.growthbook.utils.Crypto
import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.evaluators.GBExperimentHelper
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.UserContext
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.features.FetchResult
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.EvalSnapshot
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.plugin.tracking.PluginRegistry
import com.sdk.growthbook.model.StackContext
import com.sdk.growthbook.utils.GBFeaturesChangeHandler
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.sandbox.GBCachingLayerAdapter
import com.sdk.growthbook.utils.GBUtils.Companion.refreshStickyBuckets
import com.sdk.growthbook.model.diffFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.milliseconds

typealias GBTrackingCallback = (GBExperiment, GBExperimentResult) -> Unit
typealias GBFeatureUsageCallback = (featureKey: String, gbFeatureResult: GBFeatureResult) -> Unit
typealias GBExperimentRunCallback = (GBExperiment, GBExperimentResult) -> Unit

/**
 * The main export of the libraries is a simple GrowthBook wrapper class
 * that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK internal constructor(
    private val gbContext: GBContext,
    gbOptions: GBOptions,
    private val refreshHandler: GBCacheRefreshHandler?,
    networkDispatcher: NetworkDispatcher,
    features: GBFeatures? = null,
    savedGroups: Map<String, GBValue>? = null,
    cachingEnabled: Boolean,
    // Internal seam only: the public way to set a cache freshness window is
    // GBSDKBuilder.setCacheMaxAge(). Adding these to the public constructor would break binary
    // compatibility, so the public constructor below preserves the pre-7.3.0 signature and
    // delegates here.
    private val cacheMaxAge: Long?,
    // Opt-in background polling interval (ms) exposed via GBSDKBuilder.setRefreshInterval(); null
    // disables polling. Stored so startPolling() can launch the loop on demand. Defaulted so the
    // binary-compatible public constructor below can omit it.
    private val refreshInterval: Long? = null,
    // Inner stale-while-revalidate window (ms) from GBSDKBuilder.setStaleTtl(); forwarded to the
    // view model. Defaulted for the same binary-compatibility reason as above.
    staleTtl: Long? = null,
    // stale-if-error toggle from GBSDKBuilder.setServeStaleOnError(); forwarded to the view model.
    // When true, an expired cache (past cacheMaxAge, with staleTtl set) is served as a last resort
    // if the revalidating network round fails. Defaulted for binary-compatibility, as above.
    serveStaleOnError: Boolean = false,
    // Dispatcher on which the fetched payload is processed (sticky-bucket refresh + feature
    // application + refreshHandler invocation). Defaults to the platform IO dispatcher so that work
    // runs on a defined background context rather than an arbitrary thread. Overridable (e.g. with a
    // test dispatcher) so tests can drive the async pipeline deterministically.
    coroutineContext: CoroutineContext,
    private val featuresChangeHandler: GBFeaturesChangeHandler? = null,
    // Internal seam only: the public way to plug a cache is GBSDKBuilder.setCachingLayer().
    // Adding this to the public constructor would break binary compatibility,
    // so the public constructor below preserves the pre-7.4.0 signature and delegates here.
    cachingLayer: GBCachingLayer?
    ) : FeaturesFlowDelegate, IGrowthBookSDK {

    /**
     * Public constructor, kept binary-compatible with pre-7.3.0 releases. To set a cache
     * freshness window, use [com.sdk.growthbook.GBSDKBuilder.setCacheMaxAge] instead.
     */
    constructor(
        gbContext: GBContext,
        gbOptions: GBOptions,
        refreshHandler: GBCacheRefreshHandler?,
        networkDispatcher: NetworkDispatcher,
        features: GBFeatures? = null,
        savedGroups: Map<String, GBValue>? = null,
        cachingEnabled: Boolean,
    ) : this(
        gbContext = gbContext,
        gbOptions = gbOptions,
        refreshHandler = refreshHandler,
        networkDispatcher = networkDispatcher,
        features = features,
        savedGroups = savedGroups,
        cachingEnabled = cachingEnabled,
        cacheMaxAge = null,
        coroutineContext = PlatformDependentIODispatcher,
        cachingLayer = null
    )
    private var remoteSourceFeaturesFetchResult: FeaturesFetchResult =
        FeaturesFetchResult.NoResultYet
    private val gbExperimentHelper: GBExperimentHelper = GBExperimentHelper()
    private var subscriptions: MutableList<GBExperimentRunCallback> = mutableListOf()
    private var assigned: MutableMap<String, Pair<GBExperiment, GBExperimentResult>> =
        mutableMapOf()
    // True once any usable feature payload is present. Initialized from the context so
    // bundled features seeded via setInitialFeatures() before construction count as a
    // payload — otherwise a 304 arriving before the first remote fetch would be treated
    // as a failure, breaking the offline-first fallback.
    private var hasFeaturesPayload: Boolean = gbContext.features.isNotEmpty()
    var pluginRegistry: PluginRegistry? = null

    // Scope backing the reactive StateFlows ([featuresStateFlow]). Independent of the payload
    // pipeline scope (own SupervisorJob) and cancelled in [close].
    private val reactiveScope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    /**
     * Reactive stream of the full feature map. Emits the current snapshot on
     * subscription and a new value every time features are replaced — from a network
     * fetch, disk cache, [GBSDKBuilder.setInitialFeatures] seed, [setEncryptedFeatures], or SSE
     * auto-refresh. Backed by a [StateFlow], so [StateFlow.value] is always the
     * currently loaded map (the same value [getFeatures] returns).
     *
     * Hidden from Objective-C/Swift: Kotlin Flow has no native ObjC representation.
     * Apple consumers should read [getFeatures] or bridge the flow via SKIE.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    val featuresStateFlow: StateFlow<GBFeatures> =
        gbContext.snapshotFlow
            .map { it.features }
            .distinctUntilChanged()
            .stateIn(reactiveScope, SharingStarted.Eagerly, gbContext.evalSnapshot().features)

    /**
     * JAVA Consumers preset Features
     * SDK will not call API to fetch Features List
     */
    internal var featuresViewModel: FeaturesViewModel = FeaturesViewModel(
        delegate = this,
        dataSource = FeaturesDataSource(
            networkDispatcher, gbContext, gbOptions
        ),
        encryptionKey = gbContext.encryptionKey,
        cachingEnabled = cachingEnabled,
        cacheMaxAge = cacheMaxAge,
        staleTtl = staleTtl,
        serveStaleOnError = serveStaleOnError,
        cachingLayer = cachingLayer?.let { GBCachingLayerAdapter(it) } ?: CachingImpl.getLayer(),
        cacheKey = "${Constants.FEATURE_CACHE}_${gbContext.apiKey}",
        remoteEval = gbContext.remoteEval,
        remoteEvalPayloadProvider = ::buildRemoteEvalParams,
        coroutineContext = coroutineContext,
    )

    init {
        pluginRegistry = PluginRegistry(gbContext.plugins)
        pluginRegistry?.initAll()
        if (features != null) {
            gbContext.features = features
            hasFeaturesPayload = true
        } else {
            if (gbContext.remoteEval) {
                refreshForRemoteEval()
            } else {
                featuresViewModel.fetchFeatures()
            }
        }
        savedGroups?.let { gbContext.savedGroups = it }
        refreshStickyBucketService()
    }

    /**
     * Manually refreshes features from the network.
     *
     * This is an explicit refresh and always bypasses the cache freshness
     * window set via [GBSDKBuilder.setCacheMaxAge]: it hits the network even
     * if the cached features are still within their max age. In remote-eval
     * mode it re-runs the remote evaluation instead.
     */
    fun refreshCache() {
        if (gbContext.remoteEval) {
            refreshForRemoteEval()
        } else {
            featuresViewModel.revalidate()
        }
    }

    /**
     * Suspend variant of [refreshCache]: awaits the coalesced network refresh
     * (payload applied + sticky buckets refreshed) and reports whether features
     * are usable afterwards.
     *
     * @return true if the refresh succeeded. A 304 Not Modified counts as success only when a feature
     * payload has already been loaded (the cached payload stays valid); a 304 received before any
     * payload exists returns false, mirroring [featuresNotModified]. Returns false when the network
     * round failed.
     *
     * In remote-eval mode the underlying refresh issues the personalized remote-eval
     * POST (see [FeaturesViewModel.awaitRefresh]), the same path [suspendFeature] uses,
     * so it does not surface non-personalized definitions. A round repeatedly superseded by newer
     * remote-eval generations is re-joined up to [MAX_SUPERSEDED_ROUNDS] times before returning.
     */
    suspend fun refreshCacheSuspend(): Boolean {
        var superseded = 0
        while (true) {
            return when (featuresViewModel.awaitRefresh()) {
                FetchResult.Success -> true
                // A 304 only means "usable" if a payload was already loaded. A 304 received before
                // any payload exists is treated as a failure (mirrors featuresNotModified()), so the
                // caller retries instead of being told empty features are ready.
                FetchResult.NotModified -> hasFeaturesPayload
                FetchResult.Failed -> false
                FetchResult.Superseded ->
                    if (++superseded > MAX_SUPERSEDED_ROUNDS) hasFeaturesPayload
                    else continue
            }
        }
    }

    /**
     * Reactive stream of a single feature's evaluated [GBFeatureResult]. Re-evaluates whenever any
     * evaluation input changes — features, attributes, attribute overrides, forced features/variations
     * or saved groups (it observes the full state snapshot, not just the feature map) — and only
     * forwards distinct results.
     *
     * The reactive re-evaluations pass through a fully silent evaluation: they fire neither the
     * `featureUsageCallback` nor the experiment `trackingCallback`, do not notify registered
     * [com.sdk.growthbook.plugin.tracking.GrowthBookPlugin]s (so the built-in tracking plugin sends
     * nothing), and do not touch the shared tracking-dedup state — observing a feature via the flow
     * inflates neither usage analytics nor experiment exposures. Only explicit [feature] access
     * reports usage and fires exposures.
     *
     * Hidden from Objective-C/Swift for the same reason as [featuresStateFlow].
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    fun featureFlow(id: String): Flow<GBFeatureResult> =
        gbContext.snapshotFlow
            .map { evaluateFeature(id, true) }
            .distinctUntilChanged()

    /**
     * Get Context - Holding the complete data regarding cached features & attributes etc.
     */
    fun getGBContext(): GBContext {
        return gbContext
    }

    /**
     * Legacy method for enabling automatic SSE-based feature refresh.
     *
     * @deprecated Use [startAutoRefreshFeatures] instead.
     */
    @Deprecated(
        message = "Use startAutoRefreshFeatures() instead.",
        replaceWith = ReplaceWith("startAutoRefreshFeatures()"),
    )
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        return featuresViewModel.autoRefreshFeatures()
    }

    /**
     * Starts automatic SSE-based Features updates.
     *
     * This method establishes a persistent SSE connection and emits updates
     * whenever features change on the server.
     */
    fun startAutoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        return featuresViewModel.autoRefreshFeatures()
    }

    /** Fully stops the SSE connection. */
    fun stopAutoRefreshFeatures() {
        featuresViewModel.stopAutoRefresh()
    }

    /**
     * Starts background polling that revalidates features every interval configured via
     * [GBSDKBuilder.setRefreshInterval]. No-op when no interval was configured or when SSE
     * auto-refresh is already active (the two are mutually exclusive). Idempotent — a second call
     * while polling runs does nothing.
     *
     * Tie this to your app's foreground lifecycle on mobile (and call [stopPolling] when
     * backgrounded) so the poller does not keep the radio awake while the app is not visible.
     */
    fun startPolling() {
        val interval = refreshInterval
        if (interval == null) {
            if (gbContext.enableLogging) {
                GB.log("GrowthBookSDK: startPolling ignored — no refreshInterval configured")
            }
            return
        }
        val started = featuresViewModel.startPolling(interval)
        if (!started && gbContext.enableLogging) {
            GB.log("GrowthBookSDK: startPolling ignored — SSE active or polling already running")
        }
    }

    /** Stops background polling started by [startPolling]. Safe to call when not polling. */
    fun stopPolling() {
        featuresViewModel.stopPolling()
    }

    /**
     * Releases resources held by this SDK instance: flushes registered plugins (including the
     * built-in tracking plugin) so any buffered events are sent, stops any active SSE auto-refresh
     * connection or background polling, and cancels the background coroutine scope used to process
     * fetched payloads. Call this when the instance is no longer needed (e.g. on logout, or before
     * creating a replacement instance) to avoid leaking coroutines and threads. Safe to call
     * multiple times. The instance must not be used after [close].
     */
    fun close() {
        pluginRegistry?.closeAll()
        featuresViewModel.close()
        reactiveScope.cancel()
    }

    /**
     * Get Cached Features
     */
    fun getFeatures(): GBFeatures {
        return gbContext.features
    }

    /**
     * Delegate that set to Context successfully fetched features
     */
    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        // Compute the diff only for authoritative results (network / SSE / fresh cache), against the
        // features currently applied. The non-authoritative cache pre-load that precedes a network
        // refresh must not notify, otherwise the handler double-fires on a warm start (cache, then
        // network); the subsequent authoritative result reports the real delta.
        val diff = if (isRemote) {
            featuresChangeHandler?.let { diffFeatures(gbContext.features, features) }
        } else null

        gbContext.features = features
        hasFeaturesPayload = true
        if (isRemote) {
            remoteSourceFeaturesFetchResult = FeaturesFetchResult.Success
            invokeRefreshHandler(true, null)
        }

        diff?.takeIf { it.hasChanges }?.let { featuresChangeHandler?.invoke(it) }
    }

    /**
     * Delegate that fire refreshHandler with success = true when a 304 response occurs.
     * Only treated as success when the SDK instance has a loaded feature payload.
     * Without prior state a 304 cannot guarantee features are available
     */
    override fun featuresNotModified() {
        if (!hasFeaturesPayload) {
            if (gbContext.enableLogging) {
                GB.log(
                    "GrowthBookSDK: Received 304 but no feature payload has been loaded by GrowthBook instance - treating as fetch failure so features are retried."
                )
            }
            remoteSourceFeaturesFetchResult = FeaturesFetchResult.Failed
            invokeRefreshHandler(
                false,
                GBError(Exception("304 received before any feature payload was loaded"))
            )
            return
        }
        remoteSourceFeaturesFetchResult = FeaturesFetchResult.Success

        if (gbContext.enableLogging) {
            GB.log(
                "GrowthBookSDK: Features not modified (304), cached data is still valid. " +
                    "Invoking refreshHandler with success=true"
            )
        }
        invokeRefreshHandler(true, null)
    }

    /**
     * The setEncryptedFeatures method takes an encrypted string with an encryption key
     * and then decrypts it with the default method of decrypting
     * or with a method of decrypting from the user
     */
    fun setEncryptedFeatures(
        encryptedString: String,
        encryptionKey: String,
        subtleCrypto: Crypto?
    ) {
        val feature = getFeaturesFromEncryptedFeatures(
            encryptedString = encryptedString,
            encryptionKey = encryptionKey,
            subtleCrypto = subtleCrypto
        )
        gbContext.features =
            feature ?: return
    }

    /**
     * Delegate which inform that fetching features failed
     */
    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {

        if (isRemote) {
            remoteSourceFeaturesFetchResult = FeaturesFetchResult.Failed
            invokeRefreshHandler(false, error)
        }
    }

    override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) {
        if (isRemote) {
            invokeRefreshHandler(false, error)
        }
    }

    override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) {
        gbContext.savedGroups = savedGroups.mapValues { GBValue.from(it.value) }
        if (isRemote) {
            invokeRefreshHandler(true, null)
        }
    }

    /**
     * The wrapper for the feature() method.
     * This method accesses a feature only if
     * features were successfully fetched from remote source.
     * If a call is in progress, it waits for the result. If network
     * call failed, it tries to call again.
     *
     * In remote-eval mode the retry goes through the remote-eval POST (see
     * [FeaturesViewModel.awaitRefresh] / [buildRemoteEvalParams]), so it never momentarily surfaces
     * non-personalized (unevaluated) feature definitions.
     *
     * @returns a [GBFeatureResult] object
     */
    override suspend fun suspendFeature(id: String): GBFeatureResult {
        val backOff = BackoffPolicy(
            initialDelayMs = INITIAL_RETRY_DELAY_MILLIS,
            maxDelayMs = MAX_RETRY_DELAY_MILLIS,
            maxAttempts = MAX_RETRY_ATTEMPTS,
        )
        var attempt = 0
        var superseded = 0

        while (true) {
            when (remoteSourceFeaturesFetchResult) {
                FeaturesFetchResult.Success -> return feature(id)

                FeaturesFetchResult.NoResultYet -> {
                    delay(TIME_FOR_CALL_WAIT_MILLIS.milliseconds)
                    featuresViewModel.awaitRefresh()
                }

                FeaturesFetchResult.Failed -> {
                    if (!backOff.shouldRetry(attempt)) return feature(id)
                    // A superseded round is not a failure (nothing went wrong, its payload was just
                    // discarded by a newer generation): re-join the latest generation without burning
                    // a retry attempt or applying backoff.
                    if (featuresViewModel.awaitRefresh() == FetchResult.Superseded) {
                        if (++superseded > MAX_SUPERSEDED_ROUNDS) return feature(id)
                        else continue
                    }
                    if (remoteSourceFeaturesFetchResult != FeaturesFetchResult.Failed) continue
                    val delaysMs = backOff.delayFor(attempt)
                    if (gbContext.enableLogging) {
                        GB.log("GrowthBookSDK: suspendFeature: retry attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS, waiting ${delaysMs}ms")
                    }
                    delay(delaysMs.milliseconds)
                    attempt++
                }
            }
        }
    }

    /**
     * The feature method takes a single string argument,
     * which is the unique identifier for the feature and
     * @returns a [GBFeatureResult] object
     *
     * Best-effort, synchronous read of the currently loaded state: it evaluates against whatever
     * features are in the context at call time. A remote payload is applied asynchronously (on the
     * SDK's coroutineContext, IO by default), so a call made immediately after construction — before
     * the first fetch completes — returns default/unknown values. To guarantee the fetched payload
     * (and sticky-bucket assignments) are loaded before evaluating, use [suspendFeature] instead.
     */
    override fun feature(id: String): GBFeatureResult {
        // Single atomic snapshot for every evaluation input (attributes, forced features/variations,
        // attribute overrides, sticky docs), so a concurrent setter can't yield a torn mix.
        val result = evaluateFeature(id, false)
        // Newly-generated sticky assignments are merged into the context per-key during evaluation
        // (see EvaluationContext.onStickyAssignmentChanged) — no whole-map write-back needed here.
        return result
    }

    private fun evaluateFeature(id: String, silent: Boolean): GBFeatureResult {
        val snapshot = gbContext.evalSnapshot()
        val evalContext = createEvaluationContext(snapshot, silent)
        val evaluator = GBFeatureEvaluator(evalContext, snapshot.forcedFeatures)
        return evaluator.evaluateFeature(featureKey = id, attributeOverrides = snapshot.attributeOverrides)
    }

    /**
     * The feature method takes a string argument,
     * which is the unique identifier, and the type of the accessed feature.
     * The supported types of accessed features are:
     * [Boolean], [String], [Number], [Short],
     * [Int], [Long], [Float], [Double], [GBJson]
     *
     * @returns a feature value typed with specified type
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Deprecated("Use featureValue() instead", ReplaceWith("featureValue<V>(id)"))
    inline fun <reified V> feature(id: String): V? {
        return extractFeatureValue(id)
    }

    /**
     * The featureValue method takes a string argument,
     * which is the unique identifier, and the type of the accessed feature.
     *
     * Boolean, string and numeric values are returned unwrapped ([Boolean], [String] and the
     * concrete [Number] subtype the payload decoded to); JSON objects and arrays are returned as
     * [GBJson] and [GBArray].
     *
     * @returns the feature value typed as [V], or null if the feature has no value or its value
     * is not a [V]
     */
    inline fun <reified V> featureValue(id: String): V? {
        return extractFeatureValue(id)
    }

    /**
     * The isOn method takes a single string argument,
     * which is the unique identifier for the feature and returns the feature state on/off
     */
    override fun isOn(featureId: String): Boolean {
        return feature(id = featureId).on
    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    override fun run(experiment: GBExperiment): GBExperimentResult {
        val snapshot = gbContext.evalSnapshot()
        val evalContext = createEvaluationContext(snapshot)
        val evaluator = GBExperimentEvaluator(
            evalContext
        )
        val result = evaluator.evaluateExperiment(
            experiment = experiment,
            attributeOverrides = snapshot.attributeOverrides
        )

        // Newly-generated sticky assignments are merged into the context per-key during evaluation
        // (see EvaluationContext.onStickyAssignmentChanged) — no whole-map write-back needed here.

        fireSubscriptions(experiment, result)
        return result
    }

    /**
     * Replaces the Map of user attributes used to assign variations.
     *
     * Sticky bucket refresh runs in the background (fire-and-forget).
     * If you use Sticky Bucketing and need to evaluate experiments immediately
     * after setting attributes, use [setAttributesSync] instead.
     */
    override fun setAttributes(attributes: Map<String, GBValue>) {
        // Single atomic update so a concurrent feature()/run() never sees the new attributes paired
        // with the previous user's stale sticky docs (the docs are repopulated by the refresh below).
        gbContext.setAttributesClearingStickyDocs(attributes)
        refreshStickyBucketService()
        refreshForRemoteEval()
    }

    /**
     * Shallow-merges [attributes] into the current user attributes (parity with the TypeScript
     * SDK's `updateAttributes`): new keys are added, existing keys are overwritten, and untouched
     * keys are preserved. To fully replace the attribute map use [setAttributes] instead.
     *
     * The merge is one level deep — nested [GBJson]/[GBArray] values are replaced wholesale, not
     * deep-merged. A key mapped to [GBNull] keeps the key with a null value (it is NOT removed);
     * remove a key by rebuilding the map with [setAttributes].
     *
     * Sticky bucket refresh runs in the background (fire-and-forget). If you use Sticky Bucketing
     * and need to evaluate experiments immediately after updating, use [updateAttributesSync].
     */
    fun updateAttributes(attributes: Map<String, GBValue>) {
        // Merge inside the context's atomic CAS loop (not read-then-setAttributes), so two concurrent
        // updateAttributes calls can't lose each other's keys. Side effects mirror setAttributes.
        gbContext.mergeAttributesClearingStickyDocs(attributes)
        refreshStickyBucketService()
        refreshForRemoteEval()
    }

    /**
     * Coroutine version of [setAttributes] that awaits sticky bucket refresh before returning.
     *
     * Note: despite the "Sync" suffix this is a suspend function — it does not block the thread.
     * Use this when you use Sticky Bucketing and need to guarantee that assignments are loaded
     * before evaluating experiments (e.g. after login or user switch).
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     sdk.setAttributesSync(loginAttributes)
     *     val result = sdk.feature("my-experiment") // sticky buckets guaranteed
     * }
     * ```
     */
    override suspend fun setAttributesSync(attributes: Map<String, GBValue>) {
        gbContext.attributes = attributes

        if (gbContext.stickyBucketService != null) {
            refreshStickyBuckets(
                context = gbContext,
                data = null,
                attributeOverrides = gbContext.attributeOverrides
            )
        }

        refreshForRemoteEval()
    }

    /**
     * Coroutine version of [updateAttributes] that awaits sticky bucket refresh before returning.
     * Shallow-merges [attributes] into the current user attributes (see [updateAttributes] for the
     * exact merge and [GBNull] semantics).
     *
     * Note: despite the "Sync" suffix this is a suspend function — it does not block the thread.
     */
    suspend fun updateAttributesSync(attributes: Map<String, GBValue>) {
        // Atomic merge (see updateAttributes); side effects mirror setAttributesSync.
        gbContext.mergeAttributes(attributes)

        if (gbContext.stickyBucketService != null) {
            refreshStickyBuckets(
                context = gbContext,
                data = null,
                attributeOverrides = gbContext.attributeOverrides
            )
        }

        refreshForRemoteEval()
    }

    /**
     * Replaces the Map of attribute overrides used for Sticky Bucketing.
     *
     * Sticky bucket refresh runs in the background (fire-and-forget).
     * If you need to guarantee assignments are loaded before evaluating experiments,
     * use [setAttributeOverridesSync] instead.
     */
    fun setAttributeOverrides(overrides: Map<String, GBValue>) {
        gbContext.attributeOverrides = overrides
        if (gbContext.stickyBucketService != null) {
            gbContext.stickyBucketAssignmentDocs = null
            refreshStickyBucketService()
        }
        refreshForRemoteEval()
    }

    /**
     * Coroutine version of [setAttributeOverrides] that awaits sticky bucket refresh before returning.
     *
     * Note: despite the "Sync" suffix this is a suspend function — it does not block the thread.
     */
    suspend fun setAttributeOverridesSync(overrides: Map<String, GBValue>) {
        gbContext.attributeOverrides = overrides

        if (gbContext.stickyBucketService != null) {
            refreshStickyBuckets(
                context = gbContext,
                data = null,
                attributeOverrides = gbContext.attributeOverrides
            )
        }

        refreshForRemoteEval()
    }

    fun getAttributeOverrides(): Map<String, Any> {
        return gbContext.attributeOverrides
    }

    fun getForcedFeatures(): Map<String, GBValue> = gbContext.forcedFeatures

    /**
     * Sets the Map of forced feature values. In remote evaluation mode this triggers a fresh
     * remote evaluation, since forced features are part of the remote-eval request payload
     * (see [GBRemoteEvalParams]).
     */
    fun setForcedFeatures(forcedFeatures: Map<String, GBValue>) {
        gbContext.forcedFeatures = forcedFeatures
        refreshForRemoteEval()
    }

    /**
     * The setForcedVariations method setup the Map of user's (forced) variations
     * to assign a specific variation (used for QA)
     */
    fun setForcedVariations(forcedVariations: Map<String, Number>) {
        gbContext.forcedVariations = forcedVariations
        refreshForRemoteEval()
    }

    /**
     * Called after the full API payload is received, before features are applied to context.
     * Awaits sticky bucket refresh so that context is consistent when featuresFetchedSuccessfully fires.
     */
    override suspend fun onPayloadReady(model: FeaturesDataModel) {
        try {
            refreshStickyBuckets(
                context = gbContext,
                data = model,
                attributeOverrides = gbContext.attributeOverrides
            )
        } catch (e: Exception) {
            if (gbContext.enableLogging) {
                GB.error("GrowthBook: Failed to refresh sticky buckets on payload ready: ${e.message}", e)
            }
        }
    }

    private fun refreshStickyBucketService(dataModel: FeaturesDataModel? = null) {
        gbContext.stickyBucketService?.coroutineScope?.launch {
            try {
                refreshStickyBuckets(
                    context = gbContext,
                    data = dataModel,
                    attributeOverrides = gbContext.attributeOverrides
                )
            } catch (e: Exception) {
                if (gbContext.enableLogging) {
                    GB.error("GrowthBook: Failed to refresh sticky bucket assignments: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Helper method for reified feature and featureValue.
     *
     * Delegates to the shared [extractValue] so this member and the
     * [IGrowthBookSDK.featureValue] extension can never disagree — see the note there.
     */
    @PublishedApi
    internal inline fun <reified V> extractFeatureValue(id: String): V? =
        this.feature(id).extractValue()

    /**
     * Builds the remote-eval request payload from the current context, or null when not in
     * remote-eval mode. Used both to kick off an eager re-evaluation ([refreshForRemoteEval]) and,
     * via a provider seam, so the coalesced retry in [FeaturesViewModel.awaitRefresh] can issue a
     * remote-eval POST with the latest context instead of a bare GET.
     */
    private fun buildRemoteEvalParams(): GBRemoteEvalParams? {
        if (!gbContext.remoteEval) {
            return null
        }
        return GBRemoteEvalParams(
            gbContext.attributes,
            gbContext.forcedFeatures, gbContext.forcedVariations
        )
    }

    /**
     * Method for sending request evaluate features remotely
     */
    private fun refreshForRemoteEval() {
        val payload = buildRemoteEvalParams() ?: return
        featuresViewModel.fetchFeatures(payload = payload)
    }

    /**
     * Invokes the consumer [refreshHandler] defensively. Fetch results are reported from the SDK's
     * background scope (and, when polling, repeatedly), so a handler that throws must never propagate:
     * that would reach the scope's uncaught-exception path (crashing the app on Android) or abort the
     * fetch pipeline mid-way. Mirrors the try/catch already guarding tracking subscriptions in
     * [fireSubscriptions].
     */
    private fun invokeRefreshHandler(isSuccess: Boolean, error: GBError?) {
        val handler = refreshHandler ?: return
        try {
            handler.invoke(isSuccess, error)
        } catch (e: Exception) {
            if (gbContext.enableLogging) {
                GB.error("GrowthBook: refreshHandler threw and was ignored: ${e.message}", e)
            }
        }
    }

    private fun fireSubscriptions(experiment: GBExperiment, experimentResult: GBExperimentResult) {
        val key = experiment.key
        // If assigned variation has changed, fire subscriptions
        val prevAssignedExperiment = this.assigned[key]
        if (prevAssignedExperiment == null
            || prevAssignedExperiment.second.inExperiment != experimentResult.inExperiment
            || prevAssignedExperiment.second.variationId != experimentResult.variationId
        ) {
            this.assigned[key] = experiment to experimentResult
        }
        for (callback in subscriptions) {
            try {
                callback.invoke(experiment, experimentResult)
            } catch (e: Exception) {
                if (gbContext.enableLogging) {
                    GB.error("Error while run subscriptions: ${e.message}", e)
                }
            }
        }
    }

    private enum class FeaturesFetchResult {
        NoResultYet, Success, Failed
    }

    private fun createEvaluationContext(
        snapshot: EvalSnapshot = gbContext.evalSnapshot(),
        silent: Boolean = false
    ) = createEvaluationContext(
        gbContext,
        // A silent (reactive) evaluation must not touch the shared tracking-dedup state:
        // GBExperimentHelper.isTracked() marks the experiment as tracked as a side effect, which
        // would suppress the real exposure on a later feature()/run() call. Give silent evals a
        // throwaway helper so the shared one stays clean (paired with the no-op trackingCallback
        // below, which keeps the silent pass from firing anything).
        if (silent) GBExperimentHelper() else gbExperimentHelper,
        snapshot,
        // Plugins are muted for a silent evaluation too, not just the callbacks: the evaluators fire
        // pluginRegistry.fireFeatureEvaluated/fireExperimentViewed right next to onFeatureUsage /
        // trackingCallback, so leaving the registry in place would let the built-in tracking plugin
        // POST an exposure for every reactive re-evaluation. Worse than the callback case: the
        // exposure fire is gated by GBExperimentHelper.isTracked(), and the throwaway helper above is
        // empty on every pass, so the dedup would never suppress anything.
        if (silent) null else pluginRegistry,
        silent
    )

    //@ThreadLocal
    internal companion object {

        // After this period of time a call status is checked again
        private const val TIME_FOR_CALL_WAIT_MILLIS = 1000L
        private const val INITIAL_RETRY_DELAY_MILLIS = 1000L
        private const val MAX_RETRY_DELAY_MILLIS = 60_000L
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val MAX_SUPERSEDED_ROUNDS = 5

        private fun createEvaluationContext(
            gbContext: GBContext,
            gbExperimentHelper: GBExperimentHelper,
            // One atomic read of the whole shared state: features, savedGroups, attributes, forced
            // features/variations, attribute overrides and the sticky-bucket docs come from the SAME
            // snapshot, so the evaluation can never observe a torn mix (e.g. new features with stale
            // sticky docs, or new attributes with old overrides). Callers pass the snapshot they read.
            snapshot: EvalSnapshot,
            pluginRegistry: PluginRegistry?,
            silent: Boolean
        ): EvaluationContext {
            return EvaluationContext(
                enabled = gbContext.enabled,
                features = snapshot.features,
                savedGroups = snapshot.savedGroups,
                gbExperimentHelper = gbExperimentHelper,
                loggingEnabled = gbContext.enableLogging,
                onFeatureUsage = if (silent) null else gbContext.onFeatureUsage,
                forcedVariations = snapshot.forcedVariations,
                trackingCallback = if (silent) { _, _ -> } else gbContext.trackingCallback,
                stickyBucketService = gbContext.stickyBucketService,
                userContext = UserContext(
                    qaMode = gbContext.qaMode,
                    attributes = snapshot.attributes,
                    stickyBucketAssignmentDocs = snapshot.stickyBucketAssignmentDocs,
                ),
                // Merge each newly-generated sticky assignment back into the shared context by its
                // single key, atomically — instead of writing the whole docs map back after
                // evaluation (which could clobber a concurrent background refresh).
                onStickyAssignmentChanged = { key, doc ->
                    gbContext.mergeStickyAssignmentDoc(key, doc)
                },
                stackContext = StackContext(null, mutableSetOf()),
                pluginRegistry = if (silent) null else pluginRegistry
            )
        }
    }
}
