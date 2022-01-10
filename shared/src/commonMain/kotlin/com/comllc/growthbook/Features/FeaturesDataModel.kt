package com.comllc.growthbook.Features

import com.comllc.growthbook.model.GBFeature


data class FeaturesDataModel<T>(
    val status : Int,
    val features : HashMap<String, GBFeature<T>>
)