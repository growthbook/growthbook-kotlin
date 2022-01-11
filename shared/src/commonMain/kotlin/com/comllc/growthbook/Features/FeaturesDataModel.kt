package com.comllc.growthbook.Features

import com.comllc.growthbook.model.GBFeature
import kotlinx.serialization.Serializable

@Serializable
data class FeaturesDataModel(
    val status : Int,
    val features : HashMap<String, GBFeature>
)