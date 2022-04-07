package com.sdk.growthbook

import com.sdk.growthbook.Evaluators.GBExperimentEvaluator
import com.sdk.growthbook.Evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import com.sdk.growthbook.Utils.GBCacheRefreshHandler
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import kotlinx.coroutines.DelicateCoroutinesApi

typealias GBTrackingCallback = (GBExperiment, GBExperimentResult) -> Unit


/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 */
abstract class SDKBuilder(
    val apiKey: String,
    val hostURL: String,
    val attributes: Map<String, Any>,
    val trackingCallback: GBTrackingCallback
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
 * SDKBuilder - Initializer for GrowthBook SDK for Apps
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 */
class GBSDKBuilderApp(
    apiKey: String, hostURL: String, attributes: Map<String, Any>,
    trackingCallback: GBTrackingCallback
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback
) {

    private var refreshHandler: GBCacheRefreshHandler? = null

    private var networkDispatcher: NetworkDispatcher = CoreNetworkClient()

    /**
     * Set Refresh Handler - Will be called when cache is refreshed
     */
    fun setRefreshHandler(refreshHandler: GBCacheRefreshHandler): GBSDKBuilderApp {
        this.refreshHandler = refreshHandler
        return this
    }

    /**
     * Set Network Client - Network Client for Making API Calls
     * Default is KTOR - integrated in SDK
     */
    fun setNetworkDispatcher(networkDispatcher: NetworkDispatcher): SDKBuilder {
        this.networkDispatcher = networkDispatcher
        return this
    }

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
            trackingCallback = trackingCallback
        )

        return GrowthBookSDK(gbContext, refreshHandler, networkDispatcher, features = null)

    }
}

/**
 * The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK() : FeaturesFlowDelegate {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private lateinit var networkDispatcher: NetworkDispatcher

    //@ThreadLocal
    internal companion object {
        internal lateinit var gbContext: GBContext
    }

    @DelicateCoroutinesApi
    internal constructor(
        context: GBContext,
        refreshHandler: GBCacheRefreshHandler?,
        networkDispatcher: NetworkDispatcher = CoreNetworkClient(),
        features: GBFeatures?
    ) : this() {
        gbContext = context
        this.refreshHandler = refreshHandler
        this.networkDispatcher = networkDispatcher

        /**
         * JAVA Consumers preset Features
         * SDK will not call API to fetch Features List
         */
        if (features != null) {
            gbContext.features = features
        } else {
            refreshCache()
        }
    }

    /**
     * Manually Refresh Cache
     */
    @DelicateCoroutinesApi
    fun refreshCache() {
        val featureVM = FeaturesViewModel(this, FeaturesDataSource(networkDispatcher))
        featureVM.fetchFeatures()
    }

    /**
     * Get Context - Holding the complete data regarding cached features & attributes etc.
     */
    fun getGBContext(): GBContext {
        return gbContext
    }

    /**
     * Get Cached Features
     */
    fun getFeatures(): GBFeatures {
        return gbContext.features
    }


    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        gbContext.features = features
        if (isRemote) {
            this.refreshHandler?.let { it(true) }
        }
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {

        if (isRemote) {
            this.refreshHandler?.let { it(false) }
        }

    }

    /**
     * The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.
     */
    fun feature(id: String): GBFeatureResult {

        return GBFeatureEvaluator().evaluateFeature(gbContext, id)

    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment): GBExperimentResult {
        return GBExperimentEvaluator().evaluateExperiment(gbContext, experiment)
    }

    /**
     * The setAttributes method replaces the Map of user attributes that are used to assign variations
     */
    fun setAttributes(attributes: Map<String, Any>) {
        gbContext.attributes = attributes
    }
}