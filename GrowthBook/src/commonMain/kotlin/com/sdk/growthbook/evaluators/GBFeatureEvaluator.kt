package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.FeatureEvalContext
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBTrackData

/**
 * Feature Evaluator Class
 * Takes Context and Feature Key
 * Returns Calculated Feature Result against that key
 */
internal class GBFeatureEvaluator(
    private val evaluationContext: EvaluationContext,
    private val forcedFeature: Map<String, Any> = emptyMap()
) {
    /**
     * Takes Context and Feature Key
     * Returns Calculated Feature Result against that key
     */
    fun evaluateFeature(
        featureKey: String,
        attributeOverrides: Map<String, GBValue>,
        evalContext: FeatureEvalContext = FeatureEvalContext(
            id = featureKey,
            evaluatedFeatures = mutableSetOf()
        ),
    ): GBFeatureResult {
        
        try {

            /**
             * Global override
             */
            if (forcedFeature.containsKey(featureKey)) {
                if (evaluationContext.loggingEnabled) {
                    println("Global override for forced feature with key: $featureKey and value ${forcedFeature[featureKey]}")
                }
                return prepareResult(
                    featureKey = featureKey,
                    gbValue = forcedFeature[featureKey]?.let(GBValue::from),
                    source = GBFeatureSource.override,
                )
            }


            /**
             * block that handle recursion
             */
            if (evaluationContext.loggingEnabled) {
                println("evaluateFeature: circular dependency detected:")
            }
            if (evalContext.evaluatedFeatures.contains(featureKey)) {
                val featureResultWhenCircularDependencyDetected = prepareResult(
                    featureKey = featureKey,
                    gbValue = null,
                    source = GBFeatureSource.cyclicPrerequisite
                )

                return featureResultWhenCircularDependencyDetected
            }
            evalContext.evaluatedFeatures.add(featureKey)

            val targetFeature: GBFeature = evaluationContext.features.getValue(featureKey)

            /**
             * Loop through the feature rules (if any)
             */
            val rules = targetFeature.rules
            if (!rules.isNullOrEmpty()) {

                ruleLoop@ for (rule in rules) {

                    /**
                     * If there are prerequisite flag(s), evaluate them
                     */
                    if (rule.parentConditions != null) {
                        for (parentCondition in rule.parentConditions) {
                            val parentResult = evaluateFeature(
                                featureKey = parentCondition.id,
                                attributeOverrides = attributeOverrides,
                                evalContext = evalContext
                            )
                            /**
                             * break out for cyclic prerequisites
                             */
                            if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                                return prepareResult(
                                    featureKey, null,
                                    GBFeatureSource.cyclicPrerequisite,
                                )
                            }

                            val evalObj = parentResult.gbValue?.let { value ->
                                mapOf("value" to value)
                            } ?: emptyMap()

                            val evalCondition = GBConditionEvaluator().evalCondition(
                                attributes = evalObj,
                                conditionObj = parentCondition.condition.let(GBValue::from),
                                savedGroups = evaluationContext.savedGroups,
                            )

                            if (!evalCondition) {

                                /**
                                 * blocking prerequisite eval failed: feature evaluation fails
                                 */
                                if (parentCondition.gate != false) {
                                    if (evaluationContext.loggingEnabled) {
                                        println("Feature blocked by prerequisite")
                                    }
                                    
                                    return prepareResult(featureKey, null, GBFeatureSource.prerequisite)
                                }
                                /**
                                 * non-blocking prerequisite eval failed: break out
                                 * of parentConditions loop, jump to the next rule
                                 */
                                continue@ruleLoop
                            }
                        }
                    }

                    /**
                     * If there are filters for who is included (e.g. namespaces)
                     */
                    if (rule.filters != null) {
                        if (GBUtils.isFilteredOut(
                                filters = rule.filters,
                                evaluationContext = evaluationContext,
                                attributeOverrides = evaluationContext.userContext.attributes,
                            )
                        ) {
                            /**
                             * Skip rule because of filters
                             */
                            continue
                        }
                    }

                    /**
                     * Feature value is being forced
                     */
                    if (rule.force != null) {

                        /**
                         * If it's a conditional rule, skip if the condition doesn't pass
                         */
                        if (rule.condition != null && !GBConditionEvaluator().evalCondition(
                                attributes = getAttributes(
                                    attributeOverrides = attributeOverrides,
                                    attributes = evaluationContext.userContext.attributes,
                                ),
                                conditionObj = rule.condition.let(GBValue::from),
                                savedGroups = evaluationContext.savedGroups,
                            )
                        ) {
                            /**
                             * Skip rule because of condition
                             */
                            continue
                        }

                        val gate1 = (evaluationContext.stickyBucketService != null)
                        val gate2 = (rule.disableStickyBucketing != true)
                        val shouldFallbackAttributeBePassed = gate1 && gate2

                        /**
                         * If this is a percentage rollout, skip if not included
                         */
                        if (!GBUtils.isIncludedInRollout(
                                seed = rule.seed ?: featureKey,
                                hashAttribute = rule.hashAttribute,
                                fallbackAttribute = if (shouldFallbackAttributeBePassed)
                                    rule.fallbackAttribute else null,
                                range = rule.range,
                                coverage = rule.coverage,
                                hashVersion = rule.hashVersion,
                                attributeOverrides = attributeOverrides,
                                attributes = evaluationContext.userContext.attributes,
                            )
                        ) {
                            /**
                             * Skip rule because user not included in rollout
                             */
                            continue
                        }

                        /**
                         * If this was a remotely evaluated experiment, fire the tracking callbacks
                         */
                        if (rule.tracks != null) {
                            rule.tracks.forEach { track: GBTrackData ->
                                if (!GBExperimentHelper().isTracked(
                                        experiment = track.experiment,
                                        result = track.result
                                    )
                                ) {
                                    evaluationContext.trackingCallback(track.experiment, track.result)
                                }
                            }
                        }

                        if (rule.range == null) {
                            if (rule.coverage != null) {
                                val key = rule.hashAttribute ?: Constants.ID_ATTRIBUTE_KEY
                                val attributeValue = evaluationContext.userContext.attributes[key]?.toString()
                                if (attributeValue.isNullOrEmpty()) {
                                    continue@ruleLoop
                                }
                                val hashFNV = GBUtils.hash(
                                    seed = rule.seed,
                                    stringValue = attributeValue,
                                    hashVersion = rule.hashVersion,
                                ) ?: 0f
                                if (hashFNV > rule.coverage) {
                                    continue@ruleLoop
                                }
                            }
                        }

                        return prepareResult(featureKey, rule.force, GBFeatureSource.force)
                    } else {

                        val variation = rule.variations
                        if (variation != null) {

                            /**
                             * For experiment rules, run an experiment
                             */
                            val exp = GBExperiment(
                                key = rule.key ?: featureKey,
                                variations = variation,
                                coverage = rule.coverage,
                                weights = rule.weights,
                                hashAttribute = rule.hashAttribute,
                                fallBackAttribute = rule.fallbackAttribute,
                                disableStickyBucketing = rule.disableStickyBucketing,
                                bucketVersion = rule.bucketVersion,
                                minBucketVersion = rule.minBucketVersion,
                                namespace = rule.namespace,
                                meta = rule.meta,
                                ranges = rule.ranges,
                                name = rule.name,
                                phase = rule.phase,
                                seed = rule.seed,
                                hashVersion = rule.hashVersion,
                                filters = rule.filters,
                                condition = rule.condition,
                                parentConditions = rule.parentConditions
                            )

                            /**
                             * Only return a value if the user is part of the experiment
                             */
                            val result = GBExperimentEvaluator(evaluationContext)
                                .evaluateExperiment(
                                    featureId = featureKey,
                                    experiment = exp,
                                    attributeOverrides = attributeOverrides
                                )
                            if (result.inExperiment && (result.passthrough != true)) {
                                return prepareResult(
                                    featureKey = featureKey,
                                    gbValue = result.value,
                                    source = GBFeatureSource.experiment,
                                    experiment = exp,
                                    experimentResult = result
                                )
                            }
                        } else {
                            continue
                        }
                    }
                }
            }

            /**
             * Return (value = defaultValue or null, source = defaultValue)
             */
            return prepareResult(
                featureKey = featureKey,
                gbValue = targetFeature.defaultValue,
                source = GBFeatureSource.defaultValue
            )
        } catch (exception: Exception) {
            /**
             * If the key doesn't exist in context.features, return immediately
             * (value = null, source = unknownFeature).
             */
            return prepareResult(
                featureKey = featureKey,
                gbValue = null,
                source = GBFeatureSource.unknownFeature
            )
        }
    }

    /**
     * This is a helper method to create a FeatureResult object.
     * Besides the passed-in arguments, there are two derived values -
     * on and off, which are just the value cast to booleans.
     */
    private fun prepareResult(
        featureKey: String,
        gbValue: GBValue?,
        source: GBFeatureSource,
        experiment: GBExperiment? = null,
        experimentResult: GBExperimentResult? = null
    ): GBFeatureResult {

        val gate2 = (gbValue is GBBoolean && !gbValue.value)
        val gate3 = (gbValue is GBNumber && (gbValue.value == 0))
        val isFalse = gbValue == null || gate2 || gate3

        //val castResult = gbValue as? V
        val gbFeatureResult = GBFeatureResult(
            gbValue = gbValue,
            on = !isFalse,
            off = isFalse,
            source = source,
            experiment = experiment,
            experimentResult = experimentResult
        )

        evaluationContext.onFeatureUsage?.invoke(
            featureKey, gbFeatureResult,
        )

        return gbFeatureResult
    }

    /**
     * The method that merge together attributes of Context and override attribute
     */
    private fun getAttributes(
        attributes: Map<String, GBValue>, attributeOverrides: Map<String, GBValue>,
    ): Map<String, GBValue> {
        attributes.toMutableMap().putAll(attributeOverrides)
        return attributes
    }
}
