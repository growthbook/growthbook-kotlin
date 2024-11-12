package com.sdk.growthbook.evaluators

import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBTrackData
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.utils.toJsonElement
import com.sdk.growthbook.model.FeatureEvalContext
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.utils.OptionalProperty
import kotlinx.serialization.json.jsonObject

/**
 * Feature Evaluator Class
 * Takes Context and Feature Key
 * Returns Calculated Feature Result against that key
 */
internal class GBFeatureEvaluator {

    /**
     * Takes Context and Feature Key
     * Returns Calculated Feature Result against that key
     */
    fun evaluateFeature(
        context: GBContext,
        featureKey: String,
        attributeOverrides: Map<String, Any>,
        evalContext: FeatureEvalContext = FeatureEvalContext(
            id = featureKey,
            evaluatedFeatures = mutableSetOf()
        )
    ): GBFeatureResult {
        /**
         * This callback serves for listening for feature usage events
         */
        val onFeatureUsageCallback = context.onFeatureUsage
        
        try {

            /**
             * block that handle recursion
             */
            if (context.enableLogging) {
                println("evaluateFeature: circular dependency detected:")
            }
            if (evalContext.evaluatedFeatures.contains(featureKey)) {
                val featureResultWhenCircularDependencyDetected = prepareResult(
                    value = null,
                    source = GBFeatureSource.cyclicPrerequisite
                )
                
                onFeatureUsageCallback?.invoke(
                    featureKey, 
                    featureResultWhenCircularDependencyDetected
                )

                return featureResultWhenCircularDependencyDetected
            }
            evalContext.evaluatedFeatures.add(featureKey)

            val targetFeature: GBFeature = context.features.getValue(featureKey)

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
                                context = context,
                                featureKey = parentCondition.id,
                                attributeOverrides = attributeOverrides,
                                evalContext = evalContext
                            )
                            /**
                             * break out for cyclic prerequisites
                             */
                            if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                                val featureResultWhenCircularDependencyDetected = prepareResult(
                                    value = null,
                                    source = GBFeatureSource.cyclicPrerequisite
                                )
                                
                                onFeatureUsageCallback?.invoke(
                                    featureKey,
                                    featureResultWhenCircularDependencyDetected
                                )

                                return featureResultWhenCircularDependencyDetected
                            }

                            val evalObj = parentResult.value?.let { value ->
                                mapOf("value" to value)
                            } ?: emptyMap()

                            val evalCondition = GBConditionEvaluator().evalCondition(
                                attributes = evalObj.toJsonElement(),
                                conditionObj = parentCondition.condition,
                                savedGroups = context.savedGroups?.toJsonElement()?.jsonObject
                            )

                            if (!evalCondition) {

                                /**
                                 * blocking prerequisite eval failed: feature evaluation fails
                                 */
                                if (parentCondition.gate != false) {
                                    if (context.enableLogging) {
                                        println("Feature blocked by prerequisite")
                                    }
                                    
                                    val featureResultWhenBlockedByPrerequisite = prepareResult(
                                        value = null,
                                        source = GBFeatureSource.prerequisite
                                    )
                                    
                                    onFeatureUsageCallback?.invoke(
                                        featureKey, 
                                        featureResultWhenBlockedByPrerequisite
                                    )
                                    
                                    return featureResultWhenBlockedByPrerequisite
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
                                attributeOverrides = context.attributes,
                                context = context
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
                    if (rule.force is OptionalProperty.Present) {

                        /**
                         * If it's a conditional rule, skip if the condition doesn't pass
                         */
                        if (rule.condition != null && !GBConditionEvaluator().evalCondition(
                                attributes = getAttributes(
                                    context = context,
                                    attributeOverrides = attributeOverrides
                                ).toJsonElement(),
                                conditionObj = rule.condition,
                                savedGroups = context.savedGroups?.toJsonElement()?.jsonObject
                            )
                        ) {
                            /**
                             * Skip rule because of condition
                             */
                            continue
                        }

                        val gate1 = (context.stickyBucketService != null)
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
                                context = context,
                                attributeOverrides = attributeOverrides
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
                                    context.trackingCallback(track.experiment, track.result)
                                }
                            }
                        }

                        if (rule.range == null) {
                            if (rule.coverage != null) {
                                val key = rule.hashAttribute ?: Constants.ID_ATTRIBUTE_KEY
                                val attributeValue = context.attributes[key]?.toString()
                                if (attributeValue.isNullOrEmpty()) {
                                    continue@ruleLoop
                                }
                                val hashFNV = GBUtils.hash(
                                    seed = featureKey,
                                    stringValue = attributeValue,
                                    hashVersion = 1
                                ) ?: 0f
                                if (hashFNV > rule.coverage) {
                                    continue@ruleLoop
                                }
                            }
                        }
                        val forcedFeatureResult =
                            prepareResult(
                                value = rule.force.value,
                                source = GBFeatureSource.force
                            )

                        onFeatureUsageCallback?.invoke(
                            featureKey,
                            forcedFeatureResult
                        )

                        return forcedFeatureResult
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
                            val result = GBExperimentEvaluator()
                                .evaluateExperiment(
                                    featureId = featureKey,
                                    context = context,
                                    experiment = exp,
                                    attributeOverrides = attributeOverrides
                                )
                            if (result.inExperiment && (result.passthrough != true)) {
                                val experimentFeatureResult = prepareResult(
                                    value = result.value,
                                    source = GBFeatureSource.experiment,
                                    experiment = exp,
                                    experimentResult = result
                                )

                                onFeatureUsageCallback?.invoke(
                                    featureKey,
                                    experimentFeatureResult
                                )

                                return experimentFeatureResult
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
            val defaultFeatureResult = prepareResult(
                value = targetFeature.defaultValue,
                source = GBFeatureSource.defaultValue
            )

            onFeatureUsageCallback?.invoke(
                featureKey,
                defaultFeatureResult
            )

            return defaultFeatureResult
        } catch (exception: Exception) {
            /**
             * If the key doesn't exist in context.features, return immediately
             * (value = null, source = unknownFeature).
             */
            val emptyFeatureResult = prepareResult(
                value = null,
                source = GBFeatureSource.unknownFeature
            )

            onFeatureUsageCallback?.invoke(
                featureKey,
                emptyFeatureResult
            )

            return emptyFeatureResult
        }
    }

    /**
     * This is a helper method to create a FeatureResult object.
     * Besides the passed-in arguments, there are two derived values -
     * on and off, which are just the value cast to booleans.
     */
    private fun prepareResult(
        value: Any?,
        source: GBFeatureSource,
        experiment: GBExperiment? = null,
        experimentResult: GBExperimentResult? = null
    ): GBFeatureResult {

        val isFalse = value == null || value.toString() == "false" || value.toString()
            .isEmpty() || value.toString() == "0"


        return GBFeatureResult(
            value = GBUtils.convertToPrimitiveIfPossible(value),
            on = !isFalse,
            off = isFalse,
            source = source,
            experiment = experiment,
            experimentResult = experimentResult
        )
    }

    /**
     * The method that merge together attributes of Context and override attribute
     */
    private fun getAttributes(
        context: GBContext, attributeOverrides: Map<String, Any>,
    ): Map<String, Any> {
        context.attributes.toMutableMap().putAll(attributeOverrides)
        return context.attributes
    }
}