package com.comllc.growthbook

import com.comllc.growthbook.Configurations.Constants
import com.comllc.growthbook.model.*
import io.ktor.http.*

/*
    The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.

    It exposes two main methods: feature and run.
 */
class GrowthBookSDK {

    private lateinit var gbContext : GBContext

    companion object {
        val sharedInstance : GrowthBookSDK = GrowthBookSDK()

        fun initialize(context : GBContext) {
            sharedInstance.gbContext = context
        }
    }

    /*
    The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.
     */
    fun feature(id: String) : GBFeatureResult {

        /// TODO There are a few ordered steps to evaluate a feature

        try {
            val targetFeature : GBFeature = gbContext.features.getValue(id)

            /// Loop through the feature rules (if any)
            if (targetFeature.rules.size > 0) {

                for (rule in targetFeature.rules) {

                    /// TODO If the rule has a condition and it evaluates to false, skip this rule and continue to the next one


                    /// If rule.force is set
                    if (rule.force != null) {
                        /// If rule.coverage is set
                        if (rule.coverage != null){

                            /// Get the user hash value (context.attributes[experiment.hashAttribute || "id"]) and if empty, skip the rule
                                /// TODO check for experiment too
                            val attributeValue = gbContext.attributes.get(Constants.idAttributeKey) as? String ?: ""
                            if (attributeValue.isEmpty())
                                continue
                            else {
                                /// TODO Compute a hash using the Fowler–Noll–Vo algorithm (specifically fnv32-1a)
                                val hashFNV : Float = 0.0F
                                /// If the hash is greater than rule.coverage, skip the rule
                                if (hashFNV > rule.coverage) {
                                    continue
                                }
                            }

                        }

                        /// Return (value = forced value, source = force)
                        return GBFeatureResult(value = rule.force, source = GBFeatureSource.force)

                    } else {
                        /// Otherwise, convert the rule to an Experiment object
                            /// TODO assign featureKey if rule.trackingKey doesn't exist
                        val exp = GBExperiment(rule.trackingKey,
                            variations = rule.variations,
                            coverage = rule.coverage,
                            weights = rule.weights,
                            hashAttribute = rule.hashAttribute,
                            namespace = rule.namespace)

                        /// Run the experiment.
                        val result = run(exp)
                        if (result.inExperiment) {
                            /// TODO Otherwise, return immediately (value = assigned variation, source = experiment)
                            return GBFeatureResult(value = result.variationId, source = GBFeatureSource.experiment)
                        } else {
                            /// If result.inExperiment is false, skip this rule and continue to the next one.
                            continue
                        }
                    }

                }

            }

            /// Return (value = defaultValue or null, source = defaultValue)
            return GBFeatureResult(value = targetFeature.defaultValue, source = GBFeatureSource.defaultValue)


        } catch (exception : Exception) {
            /// If the key doesn't exist in context.features, return immediately (value = null, source = unknownFeature).
            return GBFeatureResult(value = null, source = GBFeatureSource.unknownFeature)
        }

    }

    /*
    The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment) : GBExperimentResult {
        /// TODO There are a bunch of ordered steps to run an experiment

        /// If experiment.variations has fewer than 2 variations, return immediately (not in experiment, variationId 0)
        //
        // If context.enabled is false, return immediately (not in experiment, variationId 0)
        if (experiment.variations.size < 2 || !gbContext.enabled ) {
            return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
        }

        /// If context.url contains a querystring {experiment.trackingKey}=[0-9]+, return immediately (not in experiment, variationId from querystring)
        val url = Url(gbContext.url)
        if (url.parameters.contains(experiment.trackingKey)) {
            return GBExperimentResult(inExperiment = false, variationId = url.parameters.get(experiment.trackingKey)!!, value = 0)
        }

        /// If context.forcedVariations[experiment.trackingKey] is defined, return immediately (not in experiment, forced variation)
        if (gbContext.forcedVariations.containsKey(experiment.trackingKey)) {
            return GBExperimentResult(inExperiment = false, variationId = gbContext.forcedVariations.get(experiment.trackingKey)!!, value = 0)
        }

        /// If context.overrides[experiment.trackingKey] is set, merge override properties into the experiment
        if (gbContext.overrides.containsKey(experiment.trackingKey)) {
            val overrideExp = gbContext.overrides.getValue(experiment.trackingKey)

            experiment.weights = overrideExp.weights
            experiment.active = overrideExp.active
            experiment.condition = overrideExp.condition
            experiment.coverage = overrideExp.coverage
            experiment.force = overrideExp.force
        }

        /// If experiment.action is set to false, return immediately (not in experiment, variationId 0)
        if (!(experiment.active ?: false)) {
            return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
        }

        /// Get the user hash attribute and value (context.attributes[experiment.hashAttribute || "id"]) and if empty, return immediately (not in experiment, variationId 0)
        val attributeValue = gbContext.attributes.get(experiment.trackingKey) as? String ?: gbContext.attributes.get(Constants.idAttributeKey) as? String ?: ""
        if (attributeValue.isEmpty()) {
            return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
        }

        /// TODO If experiment.namespace is set, check if hash value is included in the range and if not, return immediately (not in experiment, variationId 0)
        if (experiment.namespace != null) {
            return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
        }

        /// If experiment.include is set, call the function and if "false" is returned or it throws, return immediately (not in experiment, variationId 0)
        val include = experiment.include
        if (include != null) {
            try {
                if (!include()) {
                    return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
                }
            } catch (exception : Exception) {
                return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
            }
        }

        /// TODO If experiment.condition is set and the condition evaluates to false, return immediately (not in experiment, variationId 0)
        if (experiment.condition != null) {
            return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
        }

        // Default weights to an even split between all variations
        var weights = experiment.weights;
        if (weights != null) {
            weights = ArrayList(experiment.variations.size).fill(
                1 / experiment.variations.size
            );
        }

// Default coverage to 1 (100%)
        coverage = experiment.coverage ?? 1;



        return GBExperimentResult(inExperiment = false, variationId = "0", value = 0)
    }
}