package com.sdk.growthbook.evaluators

import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.CBContext
import com.sdk.growthbook.model.CONTEXTUAL_BANDIT_FALLBACK_LEAF_ID
import com.sdk.growthbook.model.GBBanditContext
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBTrackData
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.utils.GBUtils.Companion.getAttributes
import com.sdk.growthbook.utils.GBUtils.Companion.toHashValue

/**
 * Feature Evaluator Class
 * Takes Context and Feature Key
 * Returns Calculated Feature Result against that key
 */
internal class GBFeatureEvaluator(
    private val evaluationContext: EvaluationContext,
    private val forcedFeature: Map<String, GBValue> = emptyMap()
) {
    /**
     * Takes Context and Feature Key
     * Returns Calculated Feature Result against that key
     */
    fun evaluateFeature(
        featureKey: String,
        attributeOverrides: Map<String, GBValue>,
    ): GBFeatureResult {

        try {

            /**
             * block that handle recursion
             */
            if (evaluationContext.stackContext.evaluatedFeatures.contains(featureKey)) {
                if (evaluationContext.loggingEnabled) {
                    GB.warning("FeatureEvaluator: circular dependency detected for '$featureKey'")
                }

                val featureResultWhenCircularDependencyDetected = prepareResult(
                    featureKey = featureKey,
                    gbValue = null,
                    source = GBFeatureSource.cyclicPrerequisite
                )

                return featureResultWhenCircularDependencyDetected
            }
            evaluationContext.stackContext.evaluatedFeatures.add(featureKey)

            /**
             * Global override
             */
            if (forcedFeature.containsKey(featureKey)) {
                if (evaluationContext.loggingEnabled) {
                    GB.log("FeatureEvaluator: Global override for forced feature with key: $featureKey and value ${forcedFeature[featureKey]}")
                }
                return prepareResult(
                    featureKey = featureKey,
                    gbValue = forcedFeature[featureKey],
                    source = GBFeatureSource.override,
                )
            }

            val targetFeature: GBFeature = evaluationContext.features.getValue(featureKey)

            /**
             * Loop through the feature rules (if any)
             */
            val rules = targetFeature.rules
            if (!rules.isNullOrEmpty()) {
                val evaluatedFeatures =
                    evaluationContext.stackContext.evaluatedFeatures.toMutableSet()

                ruleLoop@ for (rule in rules) {

                    /**
                     * If there are prerequisite flag(s), evaluate them
                     */
                    if (rule.parentConditions != null) {
                        for (parentCondition in rule.parentConditions) {
                            evaluationContext.stackContext.evaluatedFeatures =
                                evaluatedFeatures.toMutableSet()

                            val parentResult = evaluateFeature(
                                featureKey = parentCondition.id,
                                attributeOverrides = attributeOverrides,
                            )
                            /**
                             * break out for cyclic prerequisites
                             */
                            if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                                return prepareResult(
                                    ruleId = rule.id,
                                    featureKey = featureKey, gbValue = null,
                                    source = GBFeatureSource.cyclicPrerequisite,
                                )
                            }

                            val evalObj = parentResult.gbValue?.let { value ->
                                mapOf("value" to value)
                            } ?: emptyMap()

                            val conditionObj = parentCondition
                                .condition.let(GBValue::from) as? GBJson
                                ?: GBJson(emptyMap())
                            val evalCondition = GBConditionEvaluator().evalCondition(
                                attributes = evalObj,
                                conditionObj = conditionObj,
                                savedGroups = evaluationContext.savedGroups,
                            )

                            if (!evalCondition) {

                                /**
                                 * blocking prerequisite eval failed: feature evaluation fails
                                 */
                                if (parentCondition.gate == true) {
                                    if (evaluationContext.loggingEnabled) {
                                        GB.log("FeatureEvaluator: Feature blocked by prerequisite")
                                    }

                                    return prepareResult(
                                        ruleId = rule.id,
                                        featureKey = featureKey,
                                        gbValue = null,
                                        source = GBFeatureSource.prerequisite
                                    )
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
                                attributeOverrides = attributeOverrides,
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
                        if (rule.conditionGB != null && !GBConditionEvaluator().evalCondition(
                                attributes = getAttributes(
                                    attributeOverrides = attributeOverrides,
                                    attributes = evaluationContext.userContext.attributes,
                                ),
                                conditionObj = rule.conditionGB,
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
                                val isTrackedFlag = evaluationContext
                                    .gbExperimentHelper
                                    .isTracked(
                                        experiment = track.experiment,
                                        result = track.result
                                    )

                                if (!isTrackedFlag) {
                                    try {
                                        evaluationContext.trackingCallback(
                                            track.experiment,
                                            track.result
                                        )
                                        evaluationContext.pluginRegistry?.fireExperimentViewed(
                                            track.experiment,
                                            track.result,
                                            evaluationContext.userContext.attributes
                                        )
                                    } catch (e: Exception) {
                                        GB.error(
                                            "FeatureEvaluator: trackingCallback exception for '${featureKey}'",
                                            e
                                        )
                                    }
                                }
                            }
                        }

                        return prepareResult(
                            ruleId = rule.id,
                            featureKey = featureKey,
                            gbValue = rule.force,
                            source = GBFeatureSource.force
                        )
                    } else {

                        val variation = rule.contextualVariations ?: rule.variations
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
                            )

                            /**
                             * Contextual bandit rule: route the user to a leaf and apply its weights.
                             */
                            if (rule.contextualBanditRef != null) {
                                buildContextualBanditExperiment(exp, rule.contextualBanditRef, attributeOverrides)
                            }

                            /**
                             * Only return a value if the user is part of the experiment
                             */
                            val result = GBExperimentEvaluator(evaluationContext)
                                .evaluateExperiment(
                                    featureId = featureKey,
                                    experiment = exp,
                                    attributeOverrides = attributeOverrides,
                                    conditionObj = rule.conditionGB,
                                )
                            if (result.inExperiment && (result.passthrough != true)) {
                                return prepareResult(
                                    ruleId = rule.id,
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
            if (evaluationContext.loggingEnabled) {
                GB.error("FeatureEvaluator: exception for '$featureKey'", exception)
            }
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
        ruleId: String? = "",
        featureKey: String,
        gbValue: GBValue?,
        source: GBFeatureSource,
        experiment: GBExperiment? = null,
        experimentResult: GBExperimentResult? = null
    ): GBFeatureResult {

        // Truthiness matches the reference (TypeScript) SDK's `off = !value`, which is plain
        // JS falsiness over the decoded value: undefined/null, false, zero of any numeric
        // type (including -0.0 and NaN) and the empty string are "off". Empty arrays and
        // objects, and the string "0", are truthy in JS, so they stay "on".
        // GBValue.Unknown has no JS counterpart: it marks a value the SDK could not resolve,
        // which the reference SDK would leave as `undefined`, so it is "off" as well.
        val isNullishValue = gbValue == null || gbValue is GBNull || gbValue is GBValue.Unknown
        val isFalseValue = (gbValue is GBBoolean && !gbValue.value)
        // Compare numerically rather than with boxed equals(): GBNumber holds a Number, so
        // `value == 0` only ever matches Byte/Short/Int zero and misses 0.0f, 0.0 and 0L.
        val isZeroValue = gbValue is GBNumber &&
            gbValue.value.toDouble().let { it == 0.0 || it.isNaN() }
        val isEmptyStringValue = (gbValue is GBString && gbValue.value.isEmpty())
        val isOff = isNullishValue || isFalseValue || isZeroValue || isEmptyStringValue

        //val castResult = gbValue as? V
        val gbFeatureResult = GBFeatureResult(
            ruleId = ruleId,
            gbValue = gbValue,
            on = !isOff,
            off = isOff,
            source = source,
            experiment = experiment,
            experimentResult = experimentResult
        )

        try {
            evaluationContext.onFeatureUsage?.invoke(featureKey, gbFeatureResult)
            evaluationContext.pluginRegistry?.fireFeatureEvaluated(
                featureKey,
                gbFeatureResult,
                evaluationContext.userContext.attributes
            )
        } catch (e: Exception) {
            GB.error("FeatureEvaluator: onFeatureUsage exception for '$featureKey'", e)
        }

        return gbFeatureResult
    }

    /**
     * Contextual bandit: pick the first leaf whose condition matches the user and apply its weights
     * to [experiment], recording which leaf/weights/version were used so the result can carry them.
     * Fallbacks mirror the TS SDK: ref missing -> keep the rule's aggregate weights, no metadata;
     * no leaf matches -> aggregate (or equal) weights with a sentinel leafId.
     */
    private fun buildContextualBanditExperiment(
        experiment: GBExperiment,
        contextualBanditRef: String,
        attributeOverrides: Map<String, GBValue>
    ) {
        val cbDefinition: GBContextualBandit = evaluationContext.contextualBandits?.get(contextualBanditRef)
            ?: run {
                if (evaluationContext.loggingEnabled) {
                    GB.log(
                        "GBFeatureEvaluator: contextual bandit ref '$contextualBanditRef' not found in payload, " +
                            "using aggregate weights"
                    )
                }
                return
            }

        // Throwable, not Exception: on the JS/wasm targets a failure inside the condition evaluator
        // can surface as a plain Throwable, which would otherwise escape and kill the evaluation.
        val leaf = try {
            getContextualBanditLeaf(cbDefinition, attributeOverrides)
        } catch (e: Throwable) {
            if (evaluationContext.loggingEnabled) {
                GB.warning("GBFeatureEvaluator: contextual bandit leaf selection threw, using fallback weights")
            }
            null
        }

        if (leaf != null) {
            // Only override when the leaf actually carries weights — a malformed leaf must not wipe
            // the rule's aggregate weights (in TS the field is required, so the case cannot arise).
            leaf.weights?.let { experiment.weights = it }
            experiment.contextualBandit = CBContext(
                leafId = leaf.leafId,
                variationWeights = experiment.weights ?: GBUtils.getEqualWeights(experiment.variations.size),
                banditVersion = cbDefinition.banditVersion
            )
        } else {
            if (evaluationContext.loggingEnabled) {
                GB.log(
                    "GBFeatureEvaluator: contextual bandit '$contextualBanditRef' matched no leaf, " +
                        "using fallback weights"
                )
            }
            experiment.contextualBandit = CBContext(
                leafId = CONTEXTUAL_BANDIT_FALLBACK_LEAF_ID,
                variationWeights = experiment.weights ?: GBUtils.getEqualWeights(experiment.variations.size),
                banditVersion = cbDefinition.banditVersion
            )
        }
    }

    private fun getContextualBanditLeaf(
        cbDefinition: GBContextualBandit,
        attributeOverrides: Map<String, GBValue>
    ): GBBanditContext? {
        return cbDefinition.contexts?.firstOrNull { ctx ->
            val conditionObj = ctx.condition?.let {
                GBValue.from(it)
            } as? GBJson ?: GBJson(emptyMap())
            GBConditionEvaluator().evalCondition(
                attributes = getAttributes(
                    attributes = evaluationContext.userContext.attributes,
                    attributeOverrides = attributeOverrides
                ),
                conditionObj = conditionObj,
                savedGroups = evaluationContext.savedGroups
            )
        }
    }
}
