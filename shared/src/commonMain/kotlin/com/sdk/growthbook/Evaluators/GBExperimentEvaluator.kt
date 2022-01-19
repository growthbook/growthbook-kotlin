package com.sdk.growthbook.Evaluators

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.FNV
import com.sdk.growthbook.Utils.toJsonElement
import com.sdk.growthbook.model.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal class GBExperimentEvaluator {
    
    fun evaluateExperiment(context: GBContext, experiment: GBExperiment) : GBExperimentResult{

        /// If experiment.variations has fewer than 2 variations, return immediately (not in experiment, variationId 0)
        //
        // If context.enabled is false, return immediately (not in experiment, variationId 0)
        if (experiment.variations.size < 2 || !context.enabled ) {
            return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
        }

        /// TODO If context.url contains a querystring {experiment.trackingKey}=[0-9]+, return immediately (not in experiment, variationId from querystring)
        try {
            val url = Url(context.url)

            if (url.parameters.contains(experiment.key)) {
                var value = url.parameters.get(experiment.key)?.toIntOrNull()
                if (value != null){

                    if (value < 0 || value >= experiment.variations.size ){
                        value = 0
                    }

                    return GBExperimentResult(inExperiment = false, variationId = value, value = experiment.variations[value])
                }

            }
        } catch (error : Exception) {

        }

        /// If context.forcedVariations[experiment.trackingKey] is defined, return immediately (not in experiment, forced variation)
        val forcedVariation = context.forcedVariations.get(experiment.key)
        if (forcedVariation != null) {

            var value = forcedVariation

            if (value < 0 || value >= experiment.variations.size ){
                value = 0
            }

            return GBExperimentResult(inExperiment = false, variationId = value, value = experiment.variations[value])
        }

        /// If context.overrides[experiment.trackingKey] is set, merge override properties into the experiment
        if (context.overrides.containsKey(experiment.key)) {
            val overrideExp = context.overrides.getValue(experiment.key)

            experiment.weights = overrideExp.weights
            experiment.active = overrideExp.status == GBExperimentStatus.running
            experiment.coverage = overrideExp.coverage
            experiment.force = overrideExp.force
        }

        /// If experiment.action is set to false, return immediately (not in experiment, variationId 0)
        if (!(experiment.active)) {
            return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
        }

        /// Get the user hash attribute and value (context.attributes[experiment.hashAttribute || "id"]) and if empty, return immediately (not in experiment, variationId 0)
        val attributeValue = context.attributes.get(experiment.hashAttribute ?: Constants.idAttributeKey) as? String ?: ""
        if (attributeValue.isEmpty()) {
            return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
        }

        /// If experiment.namespace is set, check if hash value is included in the range and if not, return immediately (not in experiment, variationId 0)

        /// Compute a hash using the Fowler–Noll–Vo algorithm (specifically fnv32-1a) and assign a variation
        val hashValue =
            (FNV().hashValue(attributeValue + experiment.key));
        if (experiment.namespace != null && experiment.namespace.size > 2 && hashValue != null) {
            val rangeStart = experiment.namespace[1].jsonPrimitive.doubleOrNull
            val rangeEnd = experiment.namespace[2].jsonPrimitive.doubleOrNull

            if (rangeStart != null && rangeEnd != null && (rangeStart > hashValue || hashValue > rangeEnd)){
                return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
            }
        }

        /// If experiment.condition is set and the condition evaluates to false, return immediately (not in experiment, variationId 0)
        if (experiment.condition != null) {
            val attr = context.attributes.toJsonElement()
            if (!GBConditionEvaluator().evalCondition(attr, experiment.condition!!)) {
                return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
            }
        }


        /// Default variation weights and coverage if not specified
        var weights = experiment.weights;
        if (weights == null) {
            // Default weights to an even split between all variations
            experiment.weights = List(experiment.variations.size){1.0f / (experiment.variations.size)}

        }
        /// Default coverage to 1 (100%)
        val coverage = experiment.coverage ?: 1.0f;
        experiment.coverage = coverage

        /// Calculate bucket ranges for the variations
        // Convert weights/coverage to ranges
// 50/50 split at 100% coverage == [[0, 0.5], [0.5, 1]]
// 20/80 split with 50% coverage == [[0, 0.1], [0.2, 0.6]]
        var cumulative = 0f

        val ranges = experiment.weights?.map { weight ->
            val start = cumulative
            cumulative += weight
            val items : ArrayList<JsonPrimitive> = ArrayList()
            items.add(JsonPrimitive(""))
            items.add(JsonPrimitive(start))
            items.add(JsonPrimitive(start + (coverage * weight)))
            JsonArray(items)
        }

        var assigned = -1;
        if (hashValue != null && ranges != null) {
            var counter = 0
            ranges.forEach { range ->

                val rangeStart = range[1].jsonPrimitive.doubleOrNull
                val rangeEnd = range[2].jsonPrimitive.doubleOrNull

                if (rangeStart != null && rangeEnd != null && hashValue >= rangeStart && hashValue < rangeEnd) {
                    assigned = counter
                }
                counter++
            }
        }


        /// If not assigned a variation (assigned === -1), return immediately (not in experiment, variationId 0)
        if (assigned == -1) {
            return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
        }

        /// If experiment.force is set, return immediately (not in experiment, variationId experiment.force)
        var forceExp = experiment.force
        if (forceExp != null) {

            if (forceExp < 0 || forceExp >= experiment.variations.size) {
                forceExp = 0;
            }

            return GBExperimentResult(inExperiment = false, variationId = forceExp, value = experiment.variations[forceExp])
        }

        /// If context.qaMode is true, return immediately (not in experiment, variationId 0)
        if (context.qaMode) {
            return GBExperimentResult(inExperiment = false, variationId = 0, value = 0)
        }

        /// Fire context.trackingCallback if set and the combination of hashAttribute, hashValue, experiment.key, and variationId has not been tracked before

        val result = GBExperimentResult(inExperiment = true, variationId = assigned, value = experiment.variations.get(assigned))
        context.trackingCallback(experiment, result)

        /// Return (in experiment, assigned variation)

        return result
        
    }
    
}