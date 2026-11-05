package com.sdk.growthbook.openfeature

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.network.NetworkDispatcher
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents.EventDetails
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Which identity becomes the GrowthBook `id` attribute when an OpenFeature context has both a
 * `targetingKey` and an explicit `id` attribute.
 */
enum class IdentityPrecedence {
    /** The OpenFeature `targetingKey` wins. */
    TARGETING_KEY,

    /** The explicit `id` attribute wins. */
    ID_ATTRIBUTE,
}

/**
 * OpenFeature provider backed by a GrowthBook SDK instance that the provider creates and owns.
 *
 * Supports local evaluation with fetched, cached or supplied feature payloads. Experiment
 * exposures are reported through [trackingCallback], as with the GrowthBook SDK.
 *
 * Remote evaluation and sticky bucketing are not supported yet; configuring a sticky bucket
 * service in [configureSdk] makes initialization fail.
 *
 * @param apiKey GrowthBook client key.
 * @param apiHost GrowthBook API host.
 * @param networkDispatcher Network client used by the SDK.
 * @param streamingHost Host for streaming updates, if used.
 * @param encryptionKey Key for encrypted feature payloads.
 * @param attributes Attributes used when the initial OpenFeature context is empty.
 * @param trackingCallback Called when the user is exposed to an experiment.
 * @param identityPrecedence Which identity wins when the context has both a `targetingKey` and an `id` attribute.
 * @param initializationTimeout How long to wait for the first feature fetch before reporting an error.
 * @param configureSdk Further SDK configuration. The provider installs its own refresh and
 *   features-change handlers, replacing any set here.
 */
class GrowthBookOpenFeatureProvider(
    private val apiKey: String,
    private val apiHost: String,
    private val networkDispatcher: NetworkDispatcher,
    private val streamingHost: String? = null,
    private val encryptionKey: String? = null,
    private val attributes: Map<String, GBValue> = emptyMap(),
    private val trackingCallback: GBTrackingCallback = { _, _ -> },
    private val identityPrecedence: IdentityPrecedence = IdentityPrecedence.TARGETING_KEY,
    private val initializationTimeout: Duration = 10.seconds,
    private val configureSdk: GBSDKBuilder.() -> Unit = {},
) : FeatureProvider {

    override val hooks: List<Hook<*>> = emptyList()
    override val metadata: ProviderMetadata = GrowthBookProviderMetadata

    private val events = MutableSharedFlow<OpenFeatureProviderEvents>(extraBufferCapacity = 64)

    /** Guards the state below. Reentrant, so the SDK can call back into the provider while it's held. */
    private val lock = SynchronizedObject()
    internal var sdk: GrowthBookSDK? = null
        private set
    private var appliedContext = ContextSnapshot(null)
    private var hasUsablePayload = false
    private var initialLoad: CompletableDeferred<Boolean>? = null
    private var isInitialized = false
    private var initializationFailed = false
    private var isStale = false
    private var isClosed = false
    /** Exposures recorded during the current evaluation, reported once [lock] is released. `null` outside evaluations. */
    private var pendingExposures: MutableList<Pair<GBExperiment, GBExperimentResult>>? = null

    override fun observe(): Flow<OpenFeatureProviderEvents> = events

    // Lifecycle

    override suspend fun initialize(initialContext: EvaluationContext?) {
        val loaded = CompletableDeferred<Boolean>()
        val sdk = synchronized(lock) {
            if (isClosed) throw OpenFeatureError.ProviderFatalError("Provider closed")
            if (sdk != null) return

            // A non-empty initial context replaces the configured attributes; an empty one keeps them.
            val snapshot = ContextSnapshot(initialContext)
            appliedContext = snapshot
            initialLoad = loaded
            val sdk = makeSdk(if (snapshot.isEmpty) attributes else toAttributes(initialContext))
            this.sdk = sdk
            // Supplied or cached features make the provider usable straight away.
            if (sdk.getFeatures().isNotEmpty()) {
                hasUsablePayload = true
                loaded.complete(true)
            }
            sdk
        }

        if (sdk.getGBContext().stickyBucketService != null) {
            close()
            throw OpenFeatureError.ProviderFatalError("Sticky bucketing is not supported by this provider")
        }

        val result = withTimeoutOrNull(initializationTimeout) { loaded.await() }
        synchronized(lock) {
            initialLoad = null
            isInitialized = true
            initializationFailed = result != true
        }
        when (result) {
            true -> return
            false -> throw OpenFeatureError.GeneralError("Failed to load GrowthBook features")
            null -> throw OpenFeatureError.GeneralError("Timed out waiting for GrowthBook features")
        }
    }

    override suspend fun onContextSet(oldContext: EvaluationContext?, newContext: EvaluationContext) {
        synchronized(lock) { apply(newContext) }
    }

    /** Stops the provider and closes its GrowthBook SDK. Calling it more than once is harmless. */
    override fun shutdown() {
        close()
    }

    private fun close() {
        val sdk = synchronized(lock) {
            if (isClosed) return
            isClosed = true
            initialLoad?.complete(false)
            sdk.also { sdk = null }
        }
        sdk?.close()
    }

    // Evaluation

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?,
    ): ProviderEvaluation<Boolean> = evaluate(key, defaultValue, context, ValueConversion::boolean)

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: EvaluationContext?,
    ): ProviderEvaluation<String> = evaluate(key, defaultValue, context, ValueConversion::string)

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?,
    ): ProviderEvaluation<Int> = evaluate(key, defaultValue, context, ValueConversion::int)

    override fun getLongEvaluation(
        key: String,
        defaultValue: Long,
        context: EvaluationContext?,
    ): ProviderEvaluation<Long> = evaluate(key, defaultValue, context, ValueConversion::long)

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?,
    ): ProviderEvaluation<Double> = evaluate(key, defaultValue, context, ValueConversion::double)

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?,
    ): ProviderEvaluation<Value> = evaluate(key, defaultValue, context, ValueConversion::value)

    private fun <T> evaluate(
        key: String,
        defaultValue: T,
        context: EvaluationContext?,
        convert: (GBValue) -> T?,
    ): ProviderEvaluation<T> {
        val (outcome, exposures) = synchronized(lock) {
            pendingExposures = mutableListOf()
            val outcome = runCatching { evaluateLocked(key, defaultValue, context, convert) }
            val exposures = pendingExposures.orEmpty()
            pendingExposures = null
            outcome to exposures
        }
        // Report exposures without holding the lock, so a slow tracking callback doesn't block other evaluations.
        exposures.forEach { (experiment, result) -> track(experiment, result) }
        return outcome.getOrThrow()
    }

    /** Evaluates a flag with [lock] held, so the result always belongs to the applied context. */
    private fun <T> evaluateLocked(
        key: String,
        defaultValue: T,
        context: EvaluationContext?,
        convert: (GBValue) -> T?,
    ): ProviderEvaluation<T> {
        if (isClosed) throw OpenFeatureError.ProviderFatalError("Provider closed")
        val sdk = sdk
        if (sdk == null || !hasUsablePayload) throw OpenFeatureError.ProviderNotReadyError()

        // OpenFeature installs a new context before calling onContextSet. Applying it here keeps
        // results from belonging to the previous user.
        apply(context)

        val result = sdk.feature(key)
        when (result.source) {
            GBFeatureSource.unknownFeature -> throw OpenFeatureError.FlagNotFoundError(key)
            GBFeatureSource.cyclicPrerequisite ->
                throw OpenFeatureError.ParseError("Cyclic prerequisite for flag $key")
            else -> Unit
        }

        val variant = result.experimentResult?.key
        val value = result.gbValue
        if (value == null || value == GBNull) {
            return ProviderEvaluation(defaultValue, variant = variant, reason = Reason.DEFAULT.name)
        }
        val converted = convert(value) ?: throw OpenFeatureError.TypeMismatchError()
        return ProviderEvaluation(converted, variant = variant, reason = reason(result.source).name)
    }

    // Internals

    private fun makeSdk(attributes: Map<String, GBValue>): GrowthBookSDK =
        GBSDKBuilder(
            apiKey = apiKey,
            apiHost = apiHost,
            streamingHost = streamingHost,
            networkDispatcher = networkDispatcher,
            attributes = attributes,
            encryptionKey = encryptionKey,
            trackingCallback = ::recordExposure,
        )
            .apply(configureSdk)
            .setRefreshHandler { success, _ -> handleRefresh(success) }
            .setFeaturesChangeHandler { diff -> handleFeaturesChanged(diff.changedKeys) }
            .initialize()

    /** Queues an exposure from the current evaluation, or reports it straight away outside one. */
    private fun recordExposure(experiment: GBExperiment, result: GBExperimentResult) {
        val queued = synchronized(lock) {
            pendingExposures?.add(experiment to result) != null
        }
        if (!queued) track(experiment, result)
    }

    private fun track(experiment: GBExperiment, result: GBExperimentResult) {
        try {
            trackingCallback(experiment, result)
        } catch (e: Exception) {
            // Like the SDK, don't let a failing tracking callback fail the evaluation.
        }
    }

    /** Replaces the SDK's attributes when the context differs from the last one applied. Caller holds [lock]. */
    private fun apply(context: EvaluationContext?) {
        val sdk = sdk ?: return
        val snapshot = ContextSnapshot(context)
        if (snapshot == appliedContext) return
        sdk.setAttributes(toAttributes(context))
        appliedContext = snapshot
    }

    private fun handleRefresh(success: Boolean) {
        val event = synchronized(lock) {
            if (isClosed) return
            if (success || sdk?.getFeatures()?.isNotEmpty() == true) {
                hasUsablePayload = true
            }
            initialLoad?.let {
                it.complete(hasUsablePayload)
                return
            }
            when {
                // Recovered after a failed initialization or refresh.
                success && (isStale || initializationFailed) -> {
                    isStale = false
                    initializationFailed = false
                    OpenFeatureProviderEvents.ProviderReady()
                }
                !success && hasUsablePayload && !isStale && !initializationFailed -> {
                    isStale = true
                    OpenFeatureProviderEvents.ProviderStale(EventDetails(message = "Failed to refresh GrowthBook features"))
                }
                else -> null
            }
        }
        event?.let { events.tryEmit(it) }
    }

    private fun handleFeaturesChanged(changedKeys: Set<String>) {
        synchronized(lock) {
            if (isClosed || !isInitialized || changedKeys.isEmpty()) return
        }
        events.tryEmit(OpenFeatureProviderEvents.ProviderConfigurationChanged(EventDetails(flagsChanged = changedKeys)))
    }

    /** Converts an OpenFeature context to GrowthBook attributes, mapping `targetingKey` to `id`. */
    private fun toAttributes(context: EvaluationContext?): Map<String, GBValue> {
        if (context == null) return emptyMap()
        val result = context.asMap()
            .filterValues { !it.isNull() }
            .mapValues { (_, value) -> ValueConversion.attribute(value) }
            .toMutableMap()
        val targetingKey = context.getTargetingKey()
        if (targetingKey.isNotEmpty() &&
            (identityPrecedence == IdentityPrecedence.TARGETING_KEY || "id" !in result)
        ) {
            result["id"] = ValueConversion.attribute(Value.String(targetingKey))
        }
        return result
    }

    private fun reason(source: GBFeatureSource): Reason = when (source) {
        GBFeatureSource.experiment -> Reason.SPLIT
        GBFeatureSource.force -> Reason.TARGETING_MATCH
        GBFeatureSource.override -> Reason.STATIC
        GBFeatureSource.defaultValue -> Reason.DEFAULT
        else -> Reason.UNKNOWN
    }
}

/** Provider metadata reported to OpenFeature. */
object GrowthBookProviderMetadata : ProviderMetadata {
    override val name: String = "GrowthBook"
}

/** The parts of an OpenFeature context that affect evaluation, used to detect context changes. */
private data class ContextSnapshot(val targetingKey: String, val attributes: Map<String, Value>) {
    constructor(context: EvaluationContext?) : this(
        context?.getTargetingKey().orEmpty(),
        context?.asMap().orEmpty(),
    )

    val isEmpty: Boolean get() = targetingKey.isEmpty() && attributes.isEmpty()
}
