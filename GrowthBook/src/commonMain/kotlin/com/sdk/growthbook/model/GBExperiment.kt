package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/*
    Defines a single experiment
 */
@Serializable
class GBExperiment(
    /**
     * The globally unique tracking key for the experiment
     */
    val key: String,
    /**
     * The different variations to choose between
     */
    val variations : List<JsonElement> = ArrayList(),
    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment
     */
    val namespace : JsonArray? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    val hashAttribute: String? = null,
    /**
     * How to weight traffic between variations. Must add to 1.
     */
    var weights : List<Float>? = null,
    /**
     * If set to false, always return the control (first variation)
     */
    var active : Boolean = true,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    var coverage : Float? = null,
    /**
     * Optional targeting condition
     */
    var condition: GBCondition? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    var force : Int? = null
)

/**
 * The result of running an Experiment given a specific Context
 */
class GBExperimentResult(
    /**
     * Whether or not the user is part of the experiment
     */
    val inExperiment: Boolean,
    /**
     * The array index of the assigned variation
     */
    val variationId: Int,
    /**
     * The array value of the assigned variation
     */
    val value: Any,
    /**
     * The user attribute used to assign a variation
     */
    val hashAttribute: String? = null,
    /**
     * The value of that attribute
     */
    val hashValue: String? = null

)