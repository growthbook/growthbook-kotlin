package com.sdk.growthbook.evaluators

import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.logger.GB
import kotlin.coroutines.cancellation.CancellationException
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

        // Explicit ranges take precedence over weights in the bucketer, so no leaf's weight
        // vector can describe this assignment: bucket on the ranges and emit no bandit
        // metadata at all (matches the Python SDK; TS buckets the same way).
        if (experiment.ranges != null) {
            if (evaluationContext.loggingEnabled) {
                GB.log(
                    "GBFeatureEvaluator: contextual bandit '$contextualBanditRef' rule carries explicit " +
                        "ranges; bucketing by ranges and omitting bandit metadata"
                )
            }
            return
        }

        // Throwable, not Exception: on the JS/wasm targets a failure inside the condition evaluator
        // can surface as a plain Throwable, which would otherwise escape and kill the evaluation.
        val leaf = try {
            getContextualBanditLeaf(cbDefinition, attributeOverrides)
        } catch (c: CancellationException) {
            // Evaluations can run inside coroutines; swallowing cancellation would break it.
            throw c
        } catch (e: Throwable) {
            if (evaluationContext.loggingEnabled) {
                GB.warning("GBFeatureEvaluator: contextual bandit leaf selection threw, using fallback weights")
            }
            null
        }

        val numVariations = experiment.variations.size
        val leafId = leaf?.leafId
        val leafWeights = leaf?.weights?.takeIf { isValidWeightVector(it, numVariations) }
        if (leaf != null && leafId != null && leafWeights != null) {
            experiment.weights = leafWeights
            experiment.contextualBandit = CBContext(
                leafId = leafId,
                variationWeights = leafWeights,
                banditVersion = cbDefinition.banditVersion
            )
        } else {
            // Either no leaf matched, or the matched leaf is malformed (missing leafId, or a
            // weight vector the bucketer would reject) and cannot describe the assignment.
            // Both degrade the same way: the rule's effective weights under the fallback
            // sentinel — effective meaning after the same substitution the bucketer applies,
            // so reported propensities never disagree with the weights that actually bucketed
            // the user. (In TS these fields are required; matches Python's malformed handling.)
            if (evaluationContext.loggingEnabled) {
                val reason = if (leaf == null) "matched no leaf" else "matched a malformed leaf"
                GB.log(
                    "GBFeatureEvaluator: contextual bandit '$contextualBanditRef' $reason, " +
                        "using fallback weights"
                )
            }
            experiment.contextualBandit = CBContext(
                leafId = CONTEXTUAL_BANDIT_FALLBACK_LEAF_ID,
                variationWeights = experiment.weights
                    ?.takeIf { isValidWeightVector(it, numVariations) }
                    ?: GBUtils.getEqualWeights(numVariations),
                banditVersion = cbDefinition.banditVersion
            )
        }
    }

    /**
     * The acceptance rule the bucketer effectively applies — [GBUtils.getBucketRanges]
     * substitutes equal weights for a wrong-length vector or one whose sum is outside
     * [0.99, 1.01] — plus finite/non-negative, which the bucketer's sum check misses
     * for NaN (NaN comparisons are false, so a NaN vector slips through it).
     */
    private fun isValidWeightVector(weights: List<Float>, numVariations: Int): Boolean {
        if (weights.size != numVariations) return false
        if (weights.any { !it.isFinite() || it < 0f }) return false
        val sum = weights.sum()
        return sum >= 0.99f && sum <= 1.01f
    }

    private fun getContextualBanditLeaf(
        cbDefinition: GBContextualBandit,
        attributeOverrides: Map<String, GBValue>
    ): GBBanditContext? {
        val contexts = cbDefinition.contexts ?: return null
        // Hoisted out of the leaf loop: attribute merging and the evaluator don't vary per leaf,
        // and this runs on every evaluation of every bandit-driven feature.
        val conditionEvaluator = GBConditionEvaluator()
        val attributes = getAttributes(
            attributes = evaluationContext.userContext.attributes,
            attributeOverrides = attributeOverrides
        )
        return contexts.firstOrNull { ctx ->
            // parsedCondition is cached on the leaf: an empty object for an absent condition
            // (catch-all), null for a present but non-object one — corrupt data that must fail
            // closed, or the broken leaf would swallow every user and shadow all later leaves.
            val conditionObj = ctx.parsedCondition ?: return@firstOrNull false
            conditionEvaluator.evalCondition(
                attributes = attributes,
                conditionObj = conditionObj,
                savedGroups = evaluationContext.savedGroups
            )
        }
    }
}
