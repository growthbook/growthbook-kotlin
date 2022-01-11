package com.comllc.growthbook.Configs

import com.comllc.growthbook.model.GBExperiment
import com.comllc.growthbook.model.GBExperimentOverride
import kotlinx.serialization.Serializable

@Serializable
data class ConfigsDataModel(
    val status : Int,
    val overrides : HashMap<String, GBExperimentOverride>,
    val experiments : HashMap<String, GBExperiment>
)