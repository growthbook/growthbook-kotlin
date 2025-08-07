package com.sdk.growthbook.evaluators

import kotlinx.serialization.json.jsonObject
import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.utils.StickyBucketServiceHelper

/**
 * Experiment Evaluator Class
 * Takes Context & Experiment & returns Experiment Result
 */
internal class GBExperimentEvaluator(
    private val evaluationContext: EvaluationContext,
) {

    /**
     * Takes Context & Experiment & returns Experiment Result
     */
    fun evaluateExperiment(
        experiment: GBExperiment,
        attributeOverrides: Map<String, GBValue>,
        featureId: String? = null
    ): GBExperimentResult {

        /**
         * 1. If experiment.variations has fewer than 2 variations, return immediately
         * (not in experiment, variationId 0)
         *
         * 2. If context.enabled is false, return immediately (not in experiment, variationId 0)
         */
        if (experiment.variations.size < 2 || !evaluationContext.enabled) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 3. If context.forcedVariations[GBExperiment.key] is defined,
         * return immediately (not in experiment, forced variation)
         */
        val forcedVariation = evaluationContext.forcedVariations[experiment.key]
        if (forcedVariation != null) {
            if (evaluationContext.loggingEnabled) {
                GB.log("return forcedVariation $forcedVariation")
            }
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = forcedVariation.toString().toInt(),
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 4. If experiment.action is set to false, return immediately
         * (not in experiment, variationId 0)
         */
        if (experiment.active == false) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 5. Get the user hash attribute and value
         * (context.attributes[experiment.hashAttribute || "id"])
         * and if empty, return immediately (not in experiment, variationId 0)
         */
        val (hashAttribute, hashValue) = GBUtils.getHashAttribute(
            attr = experiment.hashAttribute,
            fallback = if (evaluationContext.stickyBucketService != null
                && experiment.disableStickyBucketing != true
            ) experiment.fallBackAttribute else null,
            attributes = evaluationContext.userContext.attributes,
            attributeOverrides = attributeOverrides,
        )

        if (hashValue.isEmpty() || hashValue == "null") {
            /**
             * Skip because missing hashAttribute
             */
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        var assigned = -1
        var foundStickyBucket = false
        var stickyBucketVersionIsBlocked = false

        if ((evaluationContext.stickyBucketService != null) && (experiment.disableStickyBucketing != true)) {
            val (variation, versionIsBlocked) = GBUtils.getStickyBucketVariation(
                experimentKey = experiment.key,
                userContext = evaluationContext.userContext,
                experimentBucketVersion = experiment.bucketVersion ?: 0,
                minExperimentBucketVersion = experiment.minBucketVersion ?: 0,
                meta = experiment.meta ?: emptyList(),
                expFallBackAttribute = experiment.fallBackAttribute,
                expHashAttribute = experiment.hashAttribute,
                attributeOverrides = attributeOverrides
            )

            foundStickyBucket = variation >= 0
            assigned = variation
            stickyBucketVersionIsBlocked = versionIsBlocked ?: false
        }

        /**
         * 6. Some checks are not needed if we already have a sticky bucket
         */
        if (!foundStickyBucket) {
            /**
             * 7. Exclude if user is filtered out (used to be called "namespace")
             */
            if (experiment.filters != null) {
                if (GBUtils.isFilteredOut(
                        filters = experiment.filters,
                        attributeOverrides = evaluationContext.userContext.attributes,
                        evaluationContext= evaluationContext,
                    )
                ) {
                    if (evaluationContext.loggingEnabled) {
                        println("Skip because of filters")
                    }
                    return getExperimentResult(
                        featureId = featureId,
                        variationIndex = -1,
                        hashUsed = false,
                        attributeOverrides = attributeOverrides,
                        experiment = experiment
                    )
                }
            }

            /**
             * 8. If experiment.namespace is set, check if hash value is included in the range and
             * if not, return immediately (not in experiment, variationId 0)
             */
            else if (experiment.namespace != null) {
                val namespace = GBUtils.getGBNameSpace(experiment.namespace)
                if (namespace != null && !GBUtils.inNamespace(
                        userId = hashValue,
                        namespace = namespace
                    )
                ) {
                    return getExperimentResult(
                        featureId = featureId,
                        experiment = experiment,
                        variationIndex = -1,
                        hashUsed = false,
                        attributeOverrides = attributeOverrides
                    )
                }
            }

            /**
             * 9. If experiment.condition is set and the condition evaluates to false,
             * return immediately (not in experiment, variationId 0)
             */
            if (experiment.condition != null) {
                val attr = evaluationContext.userContext.attributes
                val conditionObj: GBJson = experiment.condition!!.let(GBValue::from) as? GBJson
                    ?: GBJson(emptyMap())
                val evaluationResult = GBConditionEvaluator().evalCondition(
                    attr, conditionObj,
                    evaluationContext.savedGroups,
                )
                if (!evaluationResult) {
                    return getExperimentResult(
                        featureId = featureId,
                        experiment = experiment,
                        variationIndex = -1,
                        hashUsed = false,
                        attributeOverrides = attributeOverrides
                    )
                }
            }

            /**
             * 10. Exclude if prerequisites are not met
             */
            if (experiment.parentConditions != null) {
                for (parentCondition in experiment.parentConditions) {
                    val parentResult = GBFeatureEvaluator(evaluationContext)
                        .evaluateFeature(
                            featureKey = parentCondition.id,
                            attributeOverrides = parentCondition.condition
                                .jsonObject.mapValues { GBValue.from(it.value) }
                        )
                    if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                        return getExperimentResult(
                            featureId = featureId,
                            experiment = experiment,
                            variationIndex = -1,
                            hashUsed = false,
                            attributeOverrides = attributeOverrides
                        )
                    }
                    val evalObj = parentResult.gbValue?.let {
                        mapOf("value" to it)
                    } ?: emptyMap()

                    val conditionObj: GBJson = parentCondition.condition.let(GBValue::from) as? GBJson
                        ?: GBJson(emptyMap())
                    val evalCondition = GBConditionEvaluator().evalCondition(
                        attributes = evalObj,
                        conditionObj = conditionObj,
                        savedGroups = evaluationContext.savedGroups,
                    )

                    /**
                     * blocking prerequisite eval failed: feature evaluation fails
                     */
                    if (!evalCondition) {
                        if (evaluationContext.loggingEnabled) {
                            println("Feature blocked by prerequisite")
                        }
                        return getExperimentResult(
                            featureId = featureId,
                            experiment = experiment,
                            variationIndex = -1,
                            hashUsed = false,
                            attributeOverrides = attributeOverrides
                        )
                    }
                }
            }
        }

        /**
         * 11. Get the variation from the sticky bucket or get bucket ranges and choose variation
         */
        val hash = GBUtils.hash(
            stringValue = hashValue,
            hashVersion = experiment.hashVersion ?: 1,
            seed = experiment.seed ?: experiment.key
        )
        if (hash == null) {
            if (evaluationContext.loggingEnabled) {
                println("Skip because of invalid hash version")
            }
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        if (!foundStickyBucket) {
            val bucketRanges = experiment.ranges ?: GBUtils.getBucketRanges(
                experiment.variations.size,
                experiment.coverage ?: 1f,
                experiment.weights ?: emptyList()
            )
            assigned = GBUtils.chooseVariation(hash, bucketRanges)
        }

        /**
         * 12. Unenroll if any prior sticky buckets are blocked by version
         */
        if (stickyBucketVersionIsBlocked) {
            if (evaluationContext.loggingEnabled) {
                println("Skip because sticky bucket version is blocked")
            }
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides,
                bucket = null,
                stickyBucketUsed = true
            )
        }

        /**
         * 13. If not assigned a variation (assigned === -1),
         * return immediately (not in experiment, variationId 0)
         */
        if (assigned < 0) {
            if (evaluationContext.loggingEnabled) {
                println("Skip because of coverage")
            }
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 14. If experiment.force is set, return immediately
         * (not in experiment, variationId experiment.force)
         */
        val forceExp = experiment.force
        if (forceExp != null) {
            return getExperimentResult(
                featureId = featureId,
                variationIndex = forceExp,
                hashUsed = false,
                attributeOverrides = attributeOverrides,
                experiment = experiment
            )
        }

        /**
         * 15. If context.qaMode is true, return immediately (not in experiment, variationId 0)
         */
        if (evaluationContext.userContext.qaMode) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 16. Build the result object
         */
        val result = getExperimentResult(
            featureId = featureId,
            experiment = experiment,
            variationIndex = assigned,
            stickyBucketUsed = foundStickyBucket,
            hashUsed = true,
            bucket = hash,
            attributeOverrides = attributeOverrides
        )
        
        if (evaluationContext.loggingEnabled) {
            println("ExperimentResult: $result")
        }

        /**
         * 17. Persist sticky bucket
         */
        if (evaluationContext.stickyBucketService != null && experiment.disableStickyBucketing != true) {
            val stickyBucketAssignmentDocs = evaluationContext.userContext.stickyBucketAssignmentDocs

            val (key, doc, changed) =
                GBUtils.generateStickyBucketAssignmentDoc(
                    attributeName = hashAttribute,
                    attributeValue = hashValue,
                    assignments = mapOf(
                        GBUtils.getStickyBucketExperimentKey(
                            experimentKey = experiment.key,
                            experimentBucketVersion = experiment.bucketVersion ?: 0
                        ) to result.key
                    ),
                    stickyBucketAssignmentDocs = stickyBucketAssignmentDocs,
                )

            if (changed) {
                /**
                 * update local docs
                 */
                evaluationContext.userContext.stickyBucketAssignmentDocs =
                    (stickyBucketAssignmentDocs ?: emptyMap()).toMutableMap().apply {
                        this[key] = doc
                    }
                with(
                    StickyBucketServiceHelper(
                        evaluationContext.stickyBucketService
                    )
                ) {
                    /**
                     * save doc
                     */
                    saveAssignments(doc)
                }
            }
        }

        /**
         * 18. Fire context.trackingClosure if set and the combination of hashAttribute,
         * hashValue, experiment.key, and variationId has not been tracked before
         */
        if (!evaluationContext.gbExperimentHelper.isTracked(experiment, result)) {
            evaluationContext.trackingCallback(experiment, result)
        }

        /**
         * Return (in experiment, assigned variation)
         */
        return result
    }

    /**
     * This is a helper method to create an ExperimentResult object.
     */
    private fun getExperimentResult(
        experiment: GBExperiment,
        variationIndex: Int = 0,
        hashUsed: Boolean,
        featureId: String? = null,
        bucket: Float? = null,
        stickyBucketUsed: Boolean? = null,
        attributeOverrides: Map<String, GBValue>

    ): GBExperimentResult {
        var inExperiment = true
        var targetVariationIndex = variationIndex

        /**
         * If assigned variation is not valid, use the baseline
         * and mark the user as not in the experiment
         */
        if (targetVariationIndex < 0 || targetVariationIndex >= experiment.variations.size) {
            /**
             * Set to 0
             */
            targetVariationIndex = 0
            inExperiment = false
        }

        val (hashAttribute, hashValue) = GBUtils.getHashAttribute(
            attr = experiment.hashAttribute,
            fallback = if (evaluationContext.stickyBucketService != null
                && experiment.disableStickyBucketing != true
            ) experiment.fallBackAttribute else null,
            attributes = evaluationContext.userContext.attributes,
            attributeOverrides = attributeOverrides,
        )
        val experimentMeta = experiment.meta ?: emptyList()
        val meta =
            if (experimentMeta.size > targetVariationIndex)
                experimentMeta[targetVariationIndex] else null

        return GBExperimentResult(
            inExperiment = inExperiment,
            variationId = targetVariationIndex,
            value = if (targetVariationIndex < experiment.variations.size)
                experiment.variations[targetVariationIndex]
            else GBValue.Unknown,
            hashAttribute = hashAttribute,
            hashValue = hashValue,
            key = meta?.key ?: "$targetVariationIndex",
            featureId = featureId,
            hashUsed = hashUsed,
            stickyBucketUsed = stickyBucketUsed ?: false,
            name = meta?.name,
            bucket = bucket,
            passthrough = meta?.passthrough
        )
    }
}
