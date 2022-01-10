package com.comllc.growthbook.Experiments

import com.comllc.growthbook.model.GBExperimentOverride

data class ConfigsDataModel(
    val status : Int,
    val overrides : HashMap<String, GBExperimentOverride>
)