package com.sdk.growthbook

import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBCacheRefreshHandler
import kotlinx.coroutines.DelicateCoroutinesApi

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
    val remoteEval: Boolean,
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
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 */
class GBSDKBuilder(
    apiKey: String,
    hostURL: String,
    networkDispatcher: NetworkDispatcher,
    attributes: Map<String, Any>,
    encryptionKey: String? = null,
    trackingCallback: GBTrackingCallback,
    remoteEval: Boolean = false,
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher, remoteEval
) {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private var stickyBucketService: GBStickyBucketService? = null
    private var featureUsageCallback: GBFeatureUsageCallback? = null

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
    fun setPrefixForStickyBucketCachedDirectory(
        prefix: String = "gbStickyBuckets__"
    ): GBSDKBuilder {
        this.stickyBucketService = GBStickyBucketServiceImp(prefix, CachingImpl.getLayer())
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
            onFeatureUsage = featureUsageCallback,
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