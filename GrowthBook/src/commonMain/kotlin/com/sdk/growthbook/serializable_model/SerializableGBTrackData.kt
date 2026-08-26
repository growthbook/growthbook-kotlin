package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.utils.GBTrackData
import kotlinx.serialization.Serializable

/**
 * Used for remote feature evaluation to trigger the TrackingCallback. An object with 2 properties
 */
@Serializable
@ConsistentCopyVisibility
data class SerializableGBTrackData internal constructor(

    /**
     * experiment - Experiment
     */
    var experiment: SerializableGBExperiment,

    /**
     * result - ExperimentResult
     */
    var result: SerializableGBExperimentResult,
)

internal fun SerializableGBTrackData.gbDeserialize() =
    GBTrackData(
        result = result.gbDeserialize(),
        experiment = experiment.gbDeserialize(),
    )
