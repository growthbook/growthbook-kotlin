package com.sdk.growthbook

import com.sdk.growthbook.Evaluators.GBExperimentEvaluator
import com.sdk.growthbook.Evaluators.GBFeatureEvaluator
import com.sdk.growthbook.Features.FeaturesDataSource
import com.sdk.growthbook.Features.FeaturesFlowDelegate
import com.sdk.growthbook.Features.FeaturesViewModel
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
import kotlin.native.concurrent.ThreadLocal


/**
 * SDKBuilder - Initializer for GrowthBook SDK
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 */
class GBSDKBuilder(
    val apiKey: String,
    val hostURL: String,
    val attributes: HashMap<String, Any>,
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
) {

    private var qaMode: Boolean = false;
    private var forcedVariations: HashMap<String, Int> = HashMap()
    private var enabled: Boolean = true;
    private var refreshHandler : GBCacheRefreshHandler? = null
    private var networkDispatcher: NetworkDispatcher = CoreNetworkClient()

    /**
     * Set Forced Variations - Default Empty
     */
    fun setForcedVariations(forcedVariations: HashMap<String, Int> = HashMap()) : GBSDKBuilder {
        this.forcedVariations = forcedVariations
        return this
    }

    /**
     * Set QA Mode - Default Disabled
     */
    fun setQAMode(isEnabled: Boolean) : GBSDKBuilder {
        this.qaMode = isEnabled
        return this
    }

    /**
     * Set Enabled - Default Disabled - If Enabled - then experiments will be disabled
     */
    fun setEnabled(isEnabled : Boolean) : GBSDKBuilder {
        this.enabled = isEnabled
        return this
    }

    /**
     * Set Refresh Handler - Will be called when cache is refreshed
     */
    fun setRefreshHandler(refreshHandler : GBCacheRefreshHandler) : GBSDKBuilder{
        this.refreshHandler = refreshHandler
        return this
    }

    /**
     * Set Network Client - Network Client for Making API Calls
     * Default is KTOR - integrated in SDK
     */
    fun setNetworkDispatcher(networkDispatcher: NetworkDispatcher) : GBSDKBuilder {
        this.networkDispatcher = networkDispatcher
        return this
    }

    /**
     * Initialize the SDK
     */
    @DelicateCoroutinesApi
    fun initialize() : GrowthBookSDK{

        val gbContext = GBContext(apiKey = apiKey, enabled = enabled, attributes = attributes, hostURL = hostURL, qaMode = qaMode, forcedVariations = forcedVariations, trackingCallback = trackingCallback)

        val sdkInstance = GrowthBookSDK(gbContext, refreshHandler, networkDispatcher)

        return sdkInstance

    }
}

/**
 * The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK() : FeaturesFlowDelegate {

    private var refreshHandler : GBCacheRefreshHandler? = null
    private lateinit var networkDispatcher: NetworkDispatcher

    @ThreadLocal
    internal companion object {
        internal lateinit var gbContext: GBContext
    }

    @DelicateCoroutinesApi
    internal constructor(context : GBContext, refreshHandler : GBCacheRefreshHandler?, networkDispatcher: NetworkDispatcher = CoreNetworkClient()) : this(){
        gbContext = context
        this.refreshHandler = refreshHandler
        this.networkDispatcher = networkDispatcher

        refreshCache()
    }

    /**
     * Manually Refresh Cache
     */
    @DelicateCoroutinesApi
    fun refreshCache(){
        val featureVM = FeaturesViewModel(this, FeaturesDataSource(networkDispatcher))
        featureVM.fetchFeatures()
    }

    /**
     * Get Context - Holding the complete data regarding cached features & attributes etc.
     */
    fun getGBContext() : GBContext {
        return gbContext
    }

    /**
     * Get Cached Features
     */
    fun getFeatures() : GBFeatures {
        return gbContext.features
    }


    override fun featuresFetchedSuccessfully(features : GBFeatures, isRemote: Boolean) {
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
    fun feature(id: String) : GBFeatureResult {

       return GBFeatureEvaluator().evaluateFeature(gbContext, id)

    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment) : GBExperimentResult {
        return GBExperimentEvaluator().evaluateExperiment(gbContext, experiment)
    }
}