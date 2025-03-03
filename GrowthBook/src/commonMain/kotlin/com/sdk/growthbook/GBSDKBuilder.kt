package com.sdk.growthbook

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.CachingImpl
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBCacheRefreshHandler

/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption
 * Network Dispatcher - Network Dispatcher
 * Remote eval - Whether to use Remote Evaluation
 * enableLogging - Prints logging statements to stdout
 */
abstract class SDKBuilder(
    val apiKey: String,
    val hostURL: String,
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
     * Set Enabled - Default Disabled - If Enabled - then experiments will be disabled
     */
    fun setEnabled(isEnabled: Boolean): SDKBuilder {
        this.enabled = isEnabled
        return this
    }

    /**
     * This method is open to be overridden by subclasses
     */
    abstract suspend fun initialize(waitForCall: Boolean = true): GrowthBookSDK
    abstract fun javaCompatibleInitialize(): GrowthBookSDK
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
 * enableLogging - Prints logging statements to stdout
 */
class GBSDKBuilder(
    apiKey: String,
    hostURL: String,
    networkDispatcher: NetworkDispatcher,
    attributes: Map<String, GBValue>,
    encryptionKey: String? = null,
    trackingCallback: GBTrackingCallback,
    remoteEval: Boolean = false,
    enableLogging: Boolean = false,
    private val cachingEnabled: Boolean = true,
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher, remoteEval, enableLogging
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
    ): GBSDKBuilder {
        this.stickyBucketService = stickyBucketService
        return this
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
    override suspend fun initialize(waitForCall: Boolean): GrowthBookSDK {
        val gbContext = createGbContext()

        return if (waitForCall) {
            suspendCoroutine { continuationObject ->
                WaitForCallCaseHelper(
                    gbContext = gbContext,
                    onResult = {
                        continuationObject.resume(it)
                    }
                )
            }
        } else {
            GrowthBookSDK(
                gbContext,
                refreshHandler,
                networkDispatcher,
                cachingEnabled = cachingEnabled,
            )
        }
    }

    /**
     * Initialize the Kotlin SDK (non-suspend method)
     */
    override fun javaCompatibleInitialize(): GrowthBookSDK {
        val gbContext = createGbContext()
        return GrowthBookSDK(
            gbContext,
            refreshHandler,
            networkDispatcher,
        )
    }

    private fun createGbContext() =
        GBContext(
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
            enableLogging = enableLogging,
            stickyBucketService = stickyBucketService,
        )

    private inner class WaitForCallCaseHelper(
        gbContext: GBContext,
        private val onResult: (GrowthBookSDK) -> Unit
    ) {
        var growthBookSDK: GrowthBookSDK? = null
        private val handleWaitForCallCallback: () -> Unit = {
            growthBookSDK?.let(onResult)
        }

        init {
            val internalRefreshHandler: GBCacheRefreshHandler = { arg1, arg2 ->
                refreshHandler?.invoke(arg1, arg2)
                handleWaitForCallCallback.invoke()
            }
            growthBookSDK = GrowthBookSDK(
                gbContext,
                internalRefreshHandler,
                networkDispatcher,
                cachingEnabled = cachingEnabled,
            )
        }
    }
}