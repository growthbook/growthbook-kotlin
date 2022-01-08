package com.comllc.growthbook.model

/*
    Defines a single experiment
 */
class GBExperiment<T>(
    /// The globally unique tracking key for the experiment
    val trackingKey: String,
    /// The different variations to choose between
    val variations : List<T>,

    /// A callback that returns true if the user should be part of the experiment and false if they should not be
    val include : (() -> Boolean)? = null,
    /// A tuple that contains the namespace identifier, plus a range of coverage for the experiment
    val namespace : GBNameSpace? = null,
    /// All users included in the experiment will be forced into the specific variation index
    val hashAttribute: String? = null,

    weights : List<Float>? = null,
    active : Boolean? = null,
    coverage : Float? = null,
    condition: String? = null,
    force : Int? = null

) : GBExperimentOverride(weights = weights, active = active, coverage = coverage, condition = condition, force = force)

// TODO RANGE HANDLING to be checked in run method
class GBNameSpace (
    val id: String,
    val rangeStart: Number,
    val rangeEnd : Number
    )

/*
    The result of running an Experiment given a specific Context
 */
class GBExperimentResult<T>(
    /// Whether or not the user is part of the experiment
    val inExperiment: Boolean,
    /// The array index of the assigned variation
    val variationId: Int,
    /// The array value of the assigned variation
    val value: T? = null,
    /// The user attribute used to assign a variation
    val hashAttribute: String? = null,
    ///  The value of that attribute
    val hashValue: String? = null
)

open class GBExperimentOverride(
    /// How to weight traffic between variations. Must add to 1.
    var weights : List<Float>? = null,
    /// If set to false, always return the control (first variation)
    var active : Boolean? = null,
    /// What percent of users should be included in the experiment (between 0 and 1, inclusive)
    var coverage : Float? = null,

    /// TODO - Optional targeting condition
    var condition: String? = null,

    /// All users included in the experiment will be forced into the specific variation index
    var force : Int? = null
)