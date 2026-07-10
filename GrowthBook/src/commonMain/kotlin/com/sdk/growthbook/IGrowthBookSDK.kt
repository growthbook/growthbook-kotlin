package com.sdk.growthbook

import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue

interface IGrowthBookSDK {

    fun isOn(featureId: String): Boolean
    fun feature(id: String): GBFeatureResult
    suspend fun suspendFeature(id: String): GBFeatureResult
    fun run(experiment: GBExperiment): GBExperimentResult
    fun setAttributes(attributes: Map<String, GBValue>)
    suspend fun setAttributesSync(attributes: Map<String, GBValue>)
}

inline fun <reified V> IGrowthBookSDK.featureValue(id: String): V? =
    when (val v = feature(id).gbValue) {
        is GBBoolean -> v.value as? V
        is GBString -> v.value as? V
        is GBNumber -> v.value as? V
        is GBJson -> v as? V
        else         -> null
    }
