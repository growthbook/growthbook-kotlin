package com.sdk.growthbook.model

/** Origin of the feature definitions currently loaded in this SDK instance. */
enum class GBDefinitionsOrigin {
    /** No feature definitions have been loaded. */
    None,

    /** Definitions provided by the application, including a bundled seed. */
    Provided,

    /** Definitions restored from the persistent cache. */
    Cache,

    /** Expired cache applied after a network failure with stale-if-error enabled. */
    Stale,

    /** Definitions received over the network, including streaming updates. */
    Network,
}

/** A snapshot of the loaded feature definitions, independent of the latest refresh attempt. */
@ConsistentCopyVisibility
data class GBFeaturesStatus internal constructor(
    val source: GBDefinitionsOrigin,
    val featureCount: Int,
)

/** A feature evaluation and the source of the definitions used for that evaluation. */
@ConsistentCopyVisibility
data class GBFeatureResultWithSource internal constructor(
    val result: GBFeatureResult,
    /**
     * The source of the loaded definitions. An unknown feature or a forced override may
     * still be returned independently of the definition set.
     */
    val definitionsSource: GBDefinitionsOrigin,
    /** Whether the requested key was in the loaded definitions, regardless of overrides or evaluation errors. */
    val definitionPresent: Boolean,
)
