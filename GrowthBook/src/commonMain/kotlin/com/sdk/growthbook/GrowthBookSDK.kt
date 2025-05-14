package com.sdk.growthbook

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Crypto
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.GBUtils.Companion.refreshStickyBuckets
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
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.kotlinx.serialization.from

typealias GBTrackingCallback = (GBExperiment, GBExperimentResult) -> Unit
typealias GBFeatureUsageCallback = (featureKey: String, gbFeatureResult: GBFeatureResult) -> Unit
typealias GBExperimentRunCallback = (GBExperiment, GBExperimentResult) -> Unit

/**
 * The main export of the libraries is a simple GrowthBook wrapper class
 * that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK(
    private val gbContext: GBContext,
    gbOptions: GBOptions,
    private val refreshHandler: GBCacheRefreshHandler?,
    networkDispatcher: NetworkDispatcher,
    features: GBFeatures? = null,
    savedGroups: Map<String, GBValue>? = null,
    cachingEnabled: Boolean,
) : FeaturesFlowDelegate {

    private var savedGroups: Map<String, GBValue>? = emptyMap()
    private var forcedFeatures: Map<String, GBValue> = emptyMap()
    private var attributeOverrides: Map<String, GBValue> = emptyMap()
    private var remoteSourceFeaturesFetchResult: FeaturesFetchResult =
        FeaturesFetchResult.NoResultYet
    private val gbExperimentHelper: GBExperimentHelper = GBExperimentHelper()
    private var subscriptions: MutableList<GBExperimentRunCallback> = mutableListOf()
    private var assigned: MutableMap<String, Pair<GBExperiment, GBExperimentResult>> =
        mutableMapOf()

    /**
     * JAVA Consumers preset Features
     * SDK will not call API to fetch Features List
     */
    internal var featuresViewModel: FeaturesViewModel = FeaturesViewModel(
        delegate = this,
        dataSource = FeaturesDataSource(
            networkDispatcher, gbContext, gbOptions,
        ),
        encryptionKey = gbContext.encryptionKey,
        cachingEnabled = cachingEnabled,
    )

    init {
        if (features != null) {
            gbContext.features = features
        } else {
            refreshCache()
        }
        this.savedGroups = savedGroups
        refreshStickyBucketService()
    }

    /**
     * Manually Refresh Cache
     */
    fun refreshCache() {
        if (gbContext.remoteEval) {
            refreshForRemoteEval()
        } else {
            featuresViewModel.fetchFeatures()
        }
    }

    /**
     * Get Context - Holding the complete data regarding cached features & attributes etc.
     */
    fun getGBContext(): GBContext {
        return gbContext
    }

    /**
     * receive Features automatically when updated SSE
     */
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        return featuresViewModel.autoRefreshFeatures()
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
        gbContext.features = features
        if (isRemote) {
            remoteSourceFeaturesFetchResult = FeaturesFetchResult.Success
            this.refreshHandler?.invoke(true, null)
        }
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
            this.refreshHandler?.invoke(false, error)
        }
    }

    override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) {
        if (isRemote) {
            this.refreshHandler?.invoke(false, error)
        }
    }

    override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) {
        gbContext.savedGroups = savedGroups.mapValues { GBValue.from(it.value) }
        if (isRemote) {
            this.refreshHandler?.invoke(true, null)
        }
    }

    /**
     * The wrapper for the feature() method.
     * This method accesses a feature only if
     * features were successfully fetched from remote source.
     * If a call is in progress, it waits for the result. If network
     * call failed, it tries to call again.
     *
     * @returns a [GBFeatureResult] object
     */
    suspend fun suspendFeature(id: String): GBFeatureResult {
        return when(remoteSourceFeaturesFetchResult) {
            FeaturesFetchResult.Success -> {
                feature(id)
            }
            FeaturesFetchResult.NoResultYet -> {
                delay(TIME_FOR_CALL_WAIT_MILLIS)
                suspendFeature(id)
            }
            FeaturesFetchResult.Failed -> {
                featuresViewModel.fetchFeatures()
                delay(TIME_FOR_CALL_WAIT_MILLIS)
                suspendFeature(id)
            }
        }
    }

    /**
     * The feature method takes a single string argument,
     * which is the unique identifier for the feature and
     * @returns a [GBFeatureResult] object
     */
    fun feature(id: String): GBFeatureResult {
        val evaluator = GBFeatureEvaluator(
            createEvaluationContext(), this.forcedFeatures,
        )
        return evaluator.evaluateFeature(
            featureKey = id,
            attributeOverrides = attributeOverrides,
        )
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
    inline fun <reified V>feature(id: String): V? {
        val listOfSupportedTypes = listOf(
            Boolean::class, String::class,
            Number::class, Short::class, Int::class,
            Long::class, Float::class, Double::class,
            GBJson::class,
        )
        if (V::class !in listOfSupportedTypes) {
            return null
        }

        val gbFeatureResult = feature(id)
        return when(val gbResultValue = gbFeatureResult.gbValue) {
            is GBNull -> null
            is GBBoolean -> gbResultValue.value as? V
            is GBString -> gbResultValue.value as? V
            is GBNumber -> gbResultValue.value as? V
            is GBJson -> gbResultValue as? V
            is GBValue.Unknown -> null
            is GBArray -> null
            null -> null
        }
    }

    /**
     * The isOn method takes a single string argument,
     * which is the unique identifier for the feature and returns the feature state on/off
     */
    fun isOn(featureId: String): Boolean {
        return feature(id = featureId).on
    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment): GBExperimentResult {
        val evaluator = GBExperimentEvaluator(
            createEvaluationContext()
        )
        val result = evaluator.evaluateExperiment(
            experiment = experiment,
            attributeOverrides = attributeOverrides
        )

        fireSubscriptions(experiment, result)
        return result
    }

    /**
     * The setAttributes method replaces the Map of user attributes
     * that are used to assign variations
     */
    fun setAttributes(attributes: Map<String, GBValue>) {
        gbContext.attributes = attributes
        refreshStickyBucketService()
    }

    /**
     * The setAttributeOverrides method replaces the Map of user overrides attribute
     * that are used for Sticky Bucketing
     */
    fun setAttributeOverrides(overrides: Map<String, GBValue>) {
        attributeOverrides = overrides
        if (gbContext.stickyBucketService != null) {
            refreshStickyBucketService()
        }
        refreshForRemoteEval()
    }

    fun getAttributeOverrides(): Map<String, Any> {
        return attributeOverrides
    }

    fun getForcedFeatures(): Map<String, GBValue> = forcedFeatures
    fun setForcedFeatures(forcedFeatures: Map<String, GBValue>) {
        this.forcedFeatures = forcedFeatures
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
     * Delegate that call refresh Sticky Bucket Service
     * after success fetched features
     */
    override fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {
        refreshStickyBucketService(dataModel = model)
    }

    /**
     * Method for update latest attributes
     */
    private fun refreshStickyBucketService(dataModel: FeaturesDataModel? = null) {
        if (gbContext.stickyBucketService != null) {
            refreshStickyBuckets(
                context = gbContext,
                data = dataModel,
                attributeOverrides = attributeOverrides
            )
        }
    }

    /**
     * Method for sending request evaluate features remotely
     */
    private fun refreshForRemoteEval() {
        if (!gbContext.remoteEval) {
            return
        }
        val payload = GBRemoteEvalParams(
            gbContext.attributes,
            this.forcedFeatures, gbContext.forcedVariations
        )
        featuresViewModel.fetchFeatures(gbContext.remoteEval, payload)
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
                    println("Error while run subscriptions: " + e.message)
                }
            }
        }
    }

    private enum class FeaturesFetchResult {
        NoResultYet, Success, Failed
    }

    private fun createEvaluationContext() =
        createEvaluationContext(gbContext, gbExperimentHelper)

    //@ThreadLocal
    internal companion object {

        // After this period of time a call status is checked again
        private const val TIME_FOR_CALL_WAIT_MILLIS = 1000L

        private fun createEvaluationContext(
            gbContext: GBContext,
            gbExperimentHelper: GBExperimentHelper,
        ) =
            EvaluationContext(
                enabled = gbContext.enabled,
                features = gbContext.features,
                savedGroups = gbContext.savedGroups,
                gbExperimentHelper = gbExperimentHelper,
                loggingEnabled = gbContext.enableLogging,
                onFeatureUsage = gbContext.onFeatureUsage,
                forcedVariations = gbContext.forcedVariations,
                trackingCallback = gbContext.trackingCallback,
                stickyBucketService = gbContext.stickyBucketService,
                userContext = UserContext(
                    qaMode = gbContext.qaMode,
                    attributes = gbContext.attributes,
                    stickyBucketAssignmentDocs = gbContext.stickyBucketAssignmentDocs,
                )
            )
    }
}
