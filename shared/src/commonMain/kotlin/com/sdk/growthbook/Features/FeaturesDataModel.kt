package com.sdk.growthbook.Features

import com.sdk.growthbook.Utils.GBFeatures
import kotlinx.serialization.Serializable

@Serializable
internal data class FeaturesDataModel(
    val status : Int,
    val features : GBFeatures
)