package com.sdk.growthbook.evaluators

import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBBucketRange
import com.sdk.growthbook.Utils.GBUtils
import com.sdk.growthbook.Utils.toJsonElement
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBLocalContext

/**
 * Experiment Evaluator Class
 * Takes Context & Experiment & returns Experiment Result
 */
internal class GBExperimentEvaluator {

    /**
     * Takes Context & Experiment & returns Experiment Result
     */
    fun evaluateExperiment(context: GBContext, experiment: GBExperiment): GBExperimentResult {
        return evaluateExperiment(
            context.localContext,
            experiment,
            context.trackingCallback
        )
    }

    /**
     * Takes Context & Experiment & returns Experiment Result
     */
    fun evaluateExperiment(
        context: GBLocalContext,
        experiment: GBExperiment,
        gBTrackingCallback: GBTrackingCallback,
    ): GBExperimentResult {
        // If experiment.variations has fewer than 2 variations, return immediately (not in experiment, variationId 0)
        //
        // If context.enabled is false, return immediately (not in experiment, variationId 0)
        if (experiment.variations.size < 2 || !context.enabled) {
            return getExperimentResult(
                experiment = experiment,
                attributes = context.attributes
            )
        }

        // If context.forcedVariations[experiment.trackingKey] is defined, return immediately (not in experiment, forced variation)
        val forcedVariation = context.forcedVariations[experiment.key]
        if (forcedVariation != null) {
            return getExperimentResult(
                experiment = experiment,
                variationIndex = forcedVariation,
                attributes = context.attributes,
            )
        }

        // If experiment.action is set to false, return immediately (not in experiment, variationId 0)
        if (!(experiment.active)) {
            return getExperimentResult(
                experiment = experiment,
                attributes = context.attributes
            )
        }

        // Get the user hash attribute and value (context.attributes[experiment.hashAttribute || "id"]) and if empty, return immediately (not in experiment, variationId 0)
        val attributeValue =
            context.attributes[experiment.hashAttribute ?: Constants.idAttributeKey] as? String
                ?: ""
        if (attributeValue.isEmpty()) {
            return getExperimentResult(
                experiment = experiment,
                attributes = context.attributes
            )
        }

        // If experiment.namespace is set, check if hash value is included in the range and if not, return immediately (not in experiment, variationId 0)

        if (experiment.namespace != null) {
            val namespace = GBUtils.getGBNameSpace(experiment.namespace)
            if (namespace != null && !GBUtils.inNamespace(attributeValue, namespace)) {
                return getExperimentResult(
                    experiment = experiment,
                    attributes = context.attributes
                )
            }
        }

        // If experiment.condition is set and the condition evaluates to false, return immediately (not in experiment, variationId 0)
        if (experiment.condition != null) {
            val attr = context.attributes.toJsonElement()
            if (!GBConditionEvaluator().evalCondition(attr, experiment.condition!!)) {
                return getExperimentResult(
                    experiment = experiment,
                    attributes = context.attributes
                )
            }
        }

        // Default variation weights and coverage if not specified
        val weights = experiment.weights;
        if (weights == null) {
            // Default weights to an even split between all variations
            experiment.weights = GBUtils.getEqualWeights(experiment.variations.size)
        }
        // Default coverage to 1 (100%)
        val coverage = experiment.coverage ?: 1.0f;
        experiment.coverage = coverage

        // Calculate bucket ranges for the variations
        // Convert weights/coverage to ranges
        val bucketRange: List<GBBucketRange> = GBUtils.getBucketRanges(
            experiment.variations.size,
            experiment.coverage!!,
            experiment.weights!!
        )

        val hash = GBUtils.hash(attributeValue + experiment.key)
        val assigned = hash?.let { GBUtils.chooseVariation(it, bucketRange) } ?: -1

        // If not assigned a variation (assigned === -1), return immediately (not in experiment, variationId 0)
        if (assigned == -1) {
            return getExperimentResult(
                experiment = experiment,
                attributes = context.attributes
            )
        }

        // If experiment.force is set, return immediately (not in experiment, variationId experiment.force)
        val forceExp = experiment.force
        if (forceExp != null) {
            return getExperimentResult(
                experiment = experiment,
                variationIndex = forceExp,
                inExperiment = false,
                attributes = context.attributes,
            )
        }

        // If context.qaMode is true, return immediately (not in experiment, variationId 0)
        if (context.qaMode) {
            return getExperimentResult(
                attributes = context.attributes,
                experiment = experiment
            )
        }

        // Fire context.trackingCallback if set and the combination of hashAttribute, hashValue, experiment.key, and variationId has not been tracked before

        val result = getExperimentResult(
            attributes = context.attributes,
            experiment = experiment,
            variationIndex = assigned,
            inExperiment = true
        )
        gBTrackingCallback(experiment, result)

        // Return (in experiment, assigned variation)

        return result
    }

    /**
     * This is a helper method to create an ExperimentResult object.
     */
    private fun getExperimentResult(
        attributes: Map<String, Any>,
        experiment: GBExperiment,
        variationIndex: Int = 0,
        inExperiment: Boolean = false
    ): GBExperimentResult {
        var targetVariationIndex = variationIndex

        // Check whether variationIndex lies within bounds of variations size
        if (targetVariationIndex < 0 || targetVariationIndex >= experiment.variations.size) {
            // Set to 0
            targetVariationIndex = 0
        }

        var targetValue: Any = 0

        // check whether variations are non empty - then only query array against index
        if (experiment.variations.isNotEmpty()) {
            targetValue = experiment.variations[targetVariationIndex]
        }

        // Hash Attribute - used for Experiment Calculations
        val hashAttribute = experiment.hashAttribute ?: Constants.idAttributeKey;
        // Hash Value against hash attribute
        val hashValue = attributes[hashAttribute] ?: ""

        return GBExperimentResult(
            inExperiment = inExperiment,
            variationId = targetVariationIndex,
            value = targetValue,
            hashAttribute = hashAttribute,
            hashValue = hashValue as? String
        )
    }
}