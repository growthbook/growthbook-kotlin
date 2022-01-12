package com.sdk.growthbook.Configs

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentOverride
import kotlinx.serialization.Serializable

@Serializable
data class ConfigsDataModel(
    val status : Int,
    val overrides : HashMap<String, GBExperimentOverride>,
    val experiments : HashMap<String, GBExperiment>
)