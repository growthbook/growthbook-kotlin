package com.sdk.growthbook.Features

import com.sdk.growthbook.model.GBFeature
import kotlinx.serialization.Serializable

@Serializable
data class FeaturesDataModel(
    val status : Int,
    val features : HashMap<String, GBFeature>
)