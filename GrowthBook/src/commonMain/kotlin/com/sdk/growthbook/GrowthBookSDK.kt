package com.sdk.growthbook

import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Crypto
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.utils.GBUtils.Companion.refreshStickyBuckets
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow

typealias GBTrackingCallback = (GBExperiment, GBExperimentResult) -> Unit

/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 */
abstract class SDKBuilder(
    val apiKey: String,
    val hostURL: String,
    val attributes: Map<String, Any>,
    val trackingCallback: GBTrackingCallback,
    val encryptionKey: String?,
    val networkDispatcher: NetworkDispatcher,
    val remoteEval: Boolean
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
     * Set Enabled - Default Disabled - If Enabled - then experiments will be disabled
     */
    fun setEnabled(isEnabled: Boolean): SDKBuilder {
        this.enabled = isEnabled
        return this
    }

    /**
     * This method is open to be overridden by subclasses
     */
    @DelicateCoroutinesApi
    abstract fun initialize(): GrowthBookSDK
}

/**
 * SDKBuilder - Initializer for GrowthBook SDK for JAVA
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Features - GrowthBook Features Map - Synced via Web API / Web Hooks
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 */
class GBSDKBuilderJAVA(
    apiKey: String,
    hostURL: String,
    attributes: Map<String, Any>,
    val features: GBFeatures,
    trackingCallback: GBTrackingCallback,
    encryptionKey: String?,
    networkDispatcher: NetworkDispatcher,
    remoteEval: Boolean = false,
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher, remoteEval
) {
    /**
     * Initialize the JAVA SDK
     */
    @DelicateCoroutinesApi
    override fun initialize(): GrowthBookSDK {

        val gbContext = GBContext(
            apiKey = apiKey,
            enabled = enabled,
            attributes = attributes,
            hostURL = hostURL,
            qaMode = qaMode,
            forcedVariations = forcedVariations,
            trackingCallback = trackingCallback,
            encryptionKey = encryptionKey,
            remoteEval = remoteEval,
        )

        return GrowthBookSDK(gbContext, null, networkDispatcher, features)
    }
}

/**
 * SDKBuilder - Initializer for GrowthBook SDK for Apps
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 */
class GBSDKBuilder(
    apiKey: String,
    hostURL: String,
    attributes: Map<String, Any>,
    trackingCallback: GBTrackingCallback,
    encryptionKey: String? = null,
    networkDispatcher: NetworkDispatcher,
    remoteEval: Boolean = false,
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher, remoteEval
) {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private var stickyBucketService: GBStickyBucketService? = null

    /**
     * Set Refresh Handler - Will be called when cache is refreshed
     */
    fun setRefreshHandler(refreshHandler: GBCacheRefreshHandler): GBSDKBuilder {
        this.refreshHandler = refreshHandler
        return this
    }

    /**
     * Method for enable sticky bucket service
     */
    fun setStickyBucketService(
        stickyBucketService: GBStickyBucketService = GBStickyBucketServiceImp(
            localStorage = CachingImpl.getLayer()
        )
    ) {
        this.stickyBucketService = stickyBucketService
    }

    /**
     * Method for set prefix of filename in cache directory GrowthBook-KMM.
     * Structure of filename - prefix$attributeName||$attributeValue
     * Default prefix of filename `gbStickyBuckets__`
     * Example name of file be like `gbStickyBuckets__test||testAttribute.txt`
     */
    fun setPrefixForStickyBucketCachedDirectory(prefix: String = "gbStickyBuckets__"): GBSDKBuilder {
        this.stickyBucketService = GBStickyBucketServiceImp(prefix, CachingImpl.getLayer())
        return this
    }

    /**
     * Initialize the Kotlin SDK
     */
    @DelicateCoroutinesApi
    override fun initialize(): GrowthBookSDK {

        val gbContext = GBContext(
            apiKey = apiKey,
            enabled = enabled,
            attributes = attributes,
            hostURL = hostURL,
            qaMode = qaMode,
            forcedVariations = forcedVariations,
            trackingCallback = trackingCallback,
            encryptionKey = encryptionKey,
            remoteEval = remoteEval,
            stickyBucketService = stickyBucketService,
        )

        return GrowthBookSDK(
            gbContext,
            refreshHandler,
            networkDispatcher
        )
    }
}

/**
 * The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK() : FeaturesFlowDelegate {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private lateinit var networkDispatcher: NetworkDispatcher
    private lateinit var featuresViewModel: FeaturesViewModel
    private var attributeOverrides: Map<String, Any> = emptyMap()
    private var forcedFeatures: Map<String, Any> = emptyMap()

    //@ThreadLocal
    internal companion object {
        internal lateinit var gbContext: GBContext
    }

    @DelicateCoroutinesApi
    internal constructor(
        context: GBContext,
        refreshHandler: GBCacheRefreshHandler?,
        networkDispatcher: NetworkDispatcher,
        features: GBFeatures? = null
    ) : this() {
        gbContext = context
        this.refreshHandler = refreshHandler
        this.networkDispatcher = networkDispatcher
        /**
         * JAVA Consumers preset Features
         * SDK will not call API to fetch Features List
         */
        this.featuresViewModel =
            FeaturesViewModel(
                delegate = this,
                dataSource = FeaturesDataSource(dispatcher = networkDispatcher),
                encryptionKey = null
            )
        if (features != null) {
            gbContext.features = features
        } else {
            featuresViewModel.encryptionKey = gbContext.encryptionKey
            refreshCache()
        }
        this.attributeOverrides = gbContext.attributes
        refreshStickyBucketService()
    }

    /**
     * Manually Refresh Cache
     */
    @DelicateCoroutinesApi
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
     * receive Features automatically when updated
     * SSE
     */
    @OptIn(DelicateCoroutinesApi::class)
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
            this.refreshHandler?.invoke(false, error)
        }
    }

    /**
     * The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.
     */
    fun feature(id: String): GBFeatureResult {
        return GBFeatureEvaluator().evaluateFeature(
            context = gbContext,
            featureKey = id,
            attributeOverrides = attributeOverrides
        )
    }

    /**
     * The isOn method takes a single string argument, which is the unique identifier for the feature and returns the feature state on/off
     */
    fun isOn(featureDd: String): Boolean {
        return feature(id = featureDd).on
    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment): GBExperimentResult {
        return GBExperimentEvaluator().evaluateExperiment(
            context = gbContext,
            experiment = experiment,
            attributeOverrides = attributeOverrides
        )
    }

    /**
     * The setForcedFeatures method setup the Map of user's (forced) features
     */
    fun setForcedFeatures(forcedFeatures: Map<String, Any>) {
        this.forcedFeatures = forcedFeatures
    }

    /**
     * The getForcedFeatures method for mapping model object for request's body type
     */
    fun getForcedFeatures(): List<List<Any>> {
        return this.forcedFeatures.map { listOf(it.key, it.value) }
    }

    /**
     * The setAttributes method replaces the Map of user attributes that are used to assign variations
     */
    fun setAttributes(attributes: Map<String, Any>) {
        gbContext.attributes = attributes
        refreshStickyBucketService()
    }

    /**
     * The setAttributeOverrides method replaces the Map of user overrides attribute
     * that are used for Sticky Bucketing
     */
    fun setAttributeOverrides(overrides: Map<String, Any>) {
        attributeOverrides = overrides
        if (gbContext.stickyBucketService != null) {
            refreshStickyBucketService()
        }
        refreshForRemoteEval()
    }

    /**
     * The setForcedVariations method setup the Map of user's (forced) variations
     * to assign a specific variation (used for QA)
     */
    fun setForcedVariations(forcedVariations: Map<String, Any>) {
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
            GBFeatureEvaluator().evaluateFeature(
                context = gbContext,
                featureKey = "",
                attributeOverrides = attributeOverrides
            )
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
    @OptIn(DelicateCoroutinesApi::class)
    private fun refreshForRemoteEval() {
        if (!gbContext.remoteEval) {
            return
        }
        val payload = GBRemoteEvalParams(
            gbContext.attributes,
            this.getForcedFeatures(),
            gbContext.forcedVariations
        )
        featuresViewModel.fetchFeatures(gbContext.remoteEval, payload)
    }
}
