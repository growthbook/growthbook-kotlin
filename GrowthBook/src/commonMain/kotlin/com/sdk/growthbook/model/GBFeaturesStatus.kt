package com.sdk.growthbook.model

/** Origin of the feature definitions currently loaded in this SDK instance. */
enum class GBFeatureDefinitionsSource {
    /** No feature definitions have been loaded. */
    None,

    /** Definitions provided by the application, including a bundled seed. */
    Provided,

    /** Definitions restored from the persistent cache. */
    Cache,

    /** Definitions received over the network, including streaming updates. */
    Network,
}

/** A snapshot of the loaded feature definitions, independent of the latest refresh attempt. */
class GBFeaturesStatus internal constructor(
    val source: GBFeatureDefinitionsSource,
    val featureCount: Int,
)

/** A feature evaluation and the source of the definitions used for that evaluation. */
class GBFeatureResultWithSource internal constructor(
    val result: GBFeatureResult,
    /**
     * The source of the loaded definitions. An unknown feature or a forced override may
     * still be returned independently of the definition set.
     */
    val definitionsSource: GBFeatureDefinitionsSource,
)
