package com.sdk.growthbook

import com.sdk.growthbook.Configs.ConfigsDataSource
import com.sdk.growthbook.Configs.ConfigsFlowDelegate
import com.sdk.growthbook.Configs.ConfigsViewModel
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
    var enabled: Boolean = true;
    var refreshHandler : GBCacheRefreshHandler? = null
    var networkDispatcher: NetworkDispatcher = CoreNetworkClient()

    fun setQAMode(isEnabled : Boolean) : GBSDKBuilder {
        this.qaMode = isEnabled
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

        val gbContext = GBContext(apiKey = apiKey, enabled = enabled, attributes = attributes, url = hostURL, qaMode = qaMode, trackingCallback = trackingCallback)

        val sdkInstance = GrowthBookSDK(gbContext, refreshHandler, networkDispatcher)

        return sdkInstance

    }
}

/*
    The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.

    It exposes two main methods: feature and run.
 */

class GrowthBookSDK() : ConfigsFlowDelegate, FeaturesFlowDelegate {

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
        val configVM = ConfigsViewModel(this, ConfigsDataSource(networkDispatcher))
        val featureVM = FeaturesViewModel(this, FeaturesDataSource(networkDispatcher))
        configVM.fetchConfigs()
        featureVM.fetchFeatures()
    }

    fun getGBContext() : GBContext {
        return gbContext
    }

    fun getOverrides() : GBOverrides {
        return gbContext.overrides
    }

    fun getFeatures() : GBFeatures {
        return gbContext.features
    }

    override fun configsFetchedSuccessfully(configs: GBOverrides, isRemote: Boolean) {
        gbContext.overrides = configs
        if (isRemote) {
            this.refreshHandler?.let { it(true) }
        }
    }

    override fun configsFetchFailed(error: GBError, isRemote: Boolean) {
        if (isRemote) {
            this.refreshHandler?.let { it(false) }
        }
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