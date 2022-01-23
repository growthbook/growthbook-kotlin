package com.sdk.growthbook.Features

import com.sdk.growthbook.Utils.GBFeatures
import kotlinx.serialization.Serializable

/**
 * Data Object for Feature API Response
 */
@Serializable
internal data class FeaturesDataModel(
    val features : GBFeatures
)