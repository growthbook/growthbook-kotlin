package com.sdk.growthbook

import com.sdk.growthbook.Evaluators.GBConditionEvaluator
import com.sdk.growthbook.Evaluators.GBExperimentEvaluator
import com.sdk.growthbook.Evaluators.GBFeatureEvaluator
import com.sdk.growthbook.Features.FeaturesDataSource
import com.sdk.growthbook.Features.FeaturesFlowDelegate
import com.sdk.growthbook.Features.FeaturesViewModel
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import com.sdk.growthbook.Utils.*
import com.sdk.growthbook.model.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject


/*
    SDKBuilder - to pass APIKey, HostURL, UserAttributes, QAMode, Enabled
 */
class GBSDKBuilder(
    val apiKey: String,
    val hostURL: String,
    val attributes: HashMap<String, Any>,
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
) {

    var qaMode: Boolean = false;
    var forcedVariations: HashMap<String, Int> = HashMap()
    var enabled: Boolean = true;
    var refreshHandler : GBCacheRefreshHandler? = null
    var networkDispatcher: NetworkDispatcher = CoreNetworkClient()

    fun enableQAMode(forcedVariations: HashMap<String, Int> = HashMap()) : GBSDKBuilder {
        this.qaMode = true
        this.forcedVariations = forcedVariations
        return this
    }

    fun setEnabled(isEnabled : Boolean) : GBSDKBuilder {
        this.enabled = isEnabled
        return this
    }

    fun setRefreshHandler(refreshHandler : GBCacheRefreshHandler) : GBSDKBuilder{
        this.refreshHandler = refreshHandler
        return this
    }

    fun setNetworkDispatcher(networkDispatcher: NetworkDispatcher) : GBSDKBuilder {
        this.networkDispatcher = networkDispatcher
        return this
    }

    fun initialize() : GrowthBookSDK{

        val gbContext = GBContext(apiKey = apiKey, enabled = enabled, attributes = attributes, hostURL = hostURL, qaMode = qaMode, forcedVariations = forcedVariations, trackingCallback = trackingCallback)

        val sdkInstance = GrowthBookSDK(gbContext, refreshHandler, networkDispatcher)

        return sdkInstance

    }
}

/*
    The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.

    It exposes two main methods: feature and run.
 */

class GrowthBookSDK() : FeaturesFlowDelegate {

    private var refreshHandler : GBCacheRefreshHandler? = null
    private lateinit var networkDispatcher: NetworkDispatcher

    internal companion object {
        lateinit var gbContext: GBContext
    }

    internal constructor(context : GBContext, refreshHandler : GBCacheRefreshHandler?, networkDispatcher: NetworkDispatcher = CoreNetworkClient()) : this(){
        gbContext = context
        this.refreshHandler = refreshHandler
        this.networkDispatcher = networkDispatcher

        refreshCache()
    }

    fun refreshCache(){
        val featureVM = FeaturesViewModel(this, FeaturesDataSource(networkDispatcher))
        featureVM.fetchFeatures()
    }

    fun getGBContext() : GBContext {
        return gbContext
    }

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

    /*
    The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.
     */
    fun feature(id: String) : GBFeatureResult {

       return GBFeatureEvaluator().evaluateFeature(gbContext, id)

    }

    /*
    The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment) : GBExperimentResult {
        return GBExperimentEvaluator().evaluateExperiment(gbContext, experiment)
    }
}