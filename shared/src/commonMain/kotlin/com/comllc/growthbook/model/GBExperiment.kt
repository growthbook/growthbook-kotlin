package com.comllc.growthbook.model

import kotlinx.serialization.Serializable

/*
    Defines a single experiment
 */
@Serializable
class GBExperiment(
    /// The globally unique tracking key for the experiment
    val trackingKey: String,
    /// The different variations to choose between
    // TODO Handle Any
    val variations : List<String> = ArrayList(),

    /// A callback that returns true if the user should be part of the experiment and false if they should not be
    val include : (() -> Boolean)? = null,
    /// A tuple that contains the namespace identifier, plus a range of coverage for the experiment
    val namespace : GBNameSpace? = null,
    /// All users included in the experiment will be forced into the specific variation index
    val hashAttribute: String? = null,

    /// How to weight traffic between variations. Must add to 1.
    var weights : List<Float>? = null,
    /// If set to false, always return the control (first variation)
    var active : Boolean? = null,
    /// What percent of users should be included in the experiment (between 0 and 1, inclusive)
    var coverage : Float? = null,

    /// TODO - Optional targeting condition
    var condition: GBCondition? = null,

    /// All users included in the experiment will be forced into the specific variation index
    var force : Int? = null

)

// Namespace Range Handling
@Serializable
class GBNameSpace (
    val id: String,
    val rangeStart: Float,
    val rangeEnd : Float
    )

/*
    The result of running an Experiment given a specific Context
 */
class GBExperimentResult(
    /// Whether or not the user is part of the experiment
    val inExperiment: Boolean,
    /// The array index of the assigned variation
    val variationId: Int,
    /// The array value of the assigned variation
    val value: Any? = null,
    /// The user attribute used to assign a variation
    val hashAttribute: String? = null,
    ///  The value of that attribute
    val hashValue: String? = null

)

// TODO "status", "groups", "url" key check
// TODO condition, coverage & force key check
@Serializable
class GBExperimentOverride(
    /// How to weight traffic between variations. Must add to 1.
    var weights : List<Float>? = null,
    /// If set to false, always return the control (first variation)
    var active : Boolean? = null,
    /// What percent of users should be included in the experiment (between 0 and 1, inclusive)
    var coverage : Float? = null,

    /// TODO - Optional targeting condition
    var condition: GBCondition? = null,

    /// All users included in the experiment will be forced into the specific variation index
    var force : Int? = null
)