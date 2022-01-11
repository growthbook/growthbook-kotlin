package com.sdk.growthbook.Configs

import com.sdk.growthbook.Utils.GBOverrides
import kotlinx.serialization.Serializable

@Serializable
internal data class ConfigsDataModel(
    val status : Int,
    val overrides : GBOverrides
)