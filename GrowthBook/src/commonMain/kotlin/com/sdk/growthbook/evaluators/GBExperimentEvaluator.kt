package com.sdk.growthbook.evaluators

import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.utils.toJsonElement
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureSource
import kotlinx.serialization.json.jsonObject

/**
 * Experiment Evaluator Class
 * Takes Context & Experiment & returns Experiment Result
 */
internal class GBExperimentEvaluator {

    /**
     * Takes Context & Experiment & returns Experiment Result
     */
    fun evaluateExperiment(
        context: GBContext,
        experiment: GBExperiment,
        attributeOverrides: Map<String, Any>,
        featureId: String? = null
    ): GBExperimentResult {

        /**
         * 1. If experiment.variations has fewer than 2 variations, return immediately
         * (not in experiment, variationId 0)
         *
         * 2. If context.enabled is false, return immediately (not in experiment, variationId 0)
         */
        if (experiment.variations.size < 2 || !context.enabled) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 3. If context.forcedVariations[experiment.trackingKey] is defined,
         * return immediately (not in experiment, forced variation)
         */
        val forcedVariation = context.forcedVariations[experiment.key]
        if (forcedVariation != null) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                variationIndex = forcedVariation.toString().toInt(),
                gbContext = context,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        /**
         * 4. If experiment.action is set to false, return immediately
         * (not in experiment, variationId 0)
         */
        if (!(experiment.active)) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
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
            context = context,
            attr = experiment.hashAttribute,
            fallback = if (context.stickyBucketService != null
                && experiment.disableStickyBucketing != true
            ) experiment.fallBackAttribute else null,
            attributeOverrides = attributeOverrides
        )

        if (hashValue.isEmpty() || hashValue == "null") {
            /**
             * Skip because missing hashAttribute
             */
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
                variationIndex = -1,
                hashUsed = false,
                attributeOverrides = attributeOverrides
            )
        }

        var assigned = -1
        var foundStickyBucket = false
        var stickyBucketVersionIsBlocked = false

        if ((context.stickyBucketService != null) && (experiment.disableStickyBucketing != true)) {
            val (variation, versionIsBlocked) = GBUtils.getStickyBucketVariation(
                context = context,
                experimentKey = experiment.key,
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
                        attributeOverrides = context.attributes,
                        context = context
                    )
                ) {
                    print("Skip because of filters")
                    return getExperimentResult(
                        featureId = featureId,
                        gbContext = context,
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
                        gbContext = context,
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
                val attr = context.attributes.toJsonElement()
                val evaluationResult = GBConditionEvaluator().evalCondition(
                    attr, experiment.condition!!, context.savedGroups?.toJsonElement()?.jsonObject
                )
                if (!evaluationResult) {
                    return getExperimentResult(
                        featureId = featureId,
                        experiment = experiment,
                        gbContext = context,
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
                    val parentResult = GBFeatureEvaluator().evaluateFeature(
                        context = context,
                        featureKey = parentCondition.id,
                        attributeOverrides = parentCondition.condition.jsonObject.toMap()
                    )
                    if (parentResult.source == GBFeatureSource.cyclicPrerequisite) {
                        return getExperimentResult(
                            featureId = featureId,
                            experiment = experiment,
                            gbContext = context,
                            variationIndex = -1,
                            hashUsed = false,
                            attributeOverrides = attributeOverrides
                        )
                    }
                    val evalObj = parentResult.value?.let {
                        mapOf("value" to GBUtils.convertToPrimitiveIfPossible(it))
                    } ?: emptyMap()

                    val evalCondition = GBConditionEvaluator().evalCondition(
                        attributes = evalObj.toJsonElement(),
                        conditionObj = parentCondition.condition,
                        context.savedGroups?.toJsonElement()?.jsonObject
                    )

                    /**
                     * blocking prerequisite eval failed: feature evaluation fails
                     */
                    if (!evalCondition) {
                        print("Feature blocked by prerequisite")
                        return getExperimentResult(
                            featureId = featureId,
                            experiment = experiment,
                            gbContext = context,
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
            print("Skip because of invalid hash version")
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
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
            print("Skip because sticky bucket version is blocked")
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
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
            print("Skip because of coverage")
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
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
                gbContext = context,
                variationIndex = forceExp,
                hashUsed = false,
                attributeOverrides = attributeOverrides,
                experiment = experiment
            )
        }

        /**
         * 15. If context.qaMode is true, return immediately (not in experiment, variationId 0)
         */
        if (context.qaMode) {
            return getExperimentResult(
                featureId = featureId,
                experiment = experiment,
                gbContext = context,
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
            gbContext = context,
            hashUsed = true,
            bucket = hash,
            attributeOverrides = attributeOverrides
        )
        print("ExperimentResult: $result")

        /**
         * 17. Persist sticky bucket
         */
        if (context.stickyBucketService != null && experiment.disableStickyBucketing != true) {
            val (key, doc, changed) =
                GBUtils.generateStickyBucketAssignmentDoc(
                    context = context,
                    attributeName = hashAttribute,
                    attributeValue = hashValue,
                    assignments = mapOf(
                        GBUtils.getStickyBucketExperimentKey(
                            experimentKey = experiment.key,
                            experimentBucketVersion = experiment.bucketVersion ?: 0
                        ) to result.key
                    )
                )

            if (changed) {
                /**
                 * update local docs
                 */
                context.stickyBucketAssignmentDocs =
                    (context.stickyBucketAssignmentDocs ?: emptyMap()).toMutableMap().apply {
                        this[key] = doc
                    }
                /**
                 * save doc
                 */
                context.stickyBucketService.saveAssignments(doc = doc)
            }
        }

        /**
         * 18. Fire context.trackingClosure if set and the combination of hashAttribute,
         * hashValue, experiment.key, and variationId has not been tracked before
         */
        if (!context.experimentHelper.isTracked(experiment, result)) {
            context.trackingCallback(experiment, result)
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
        gbContext: GBContext,
        experiment: GBExperiment,
        variationIndex: Int = 0,
        hashUsed: Boolean,
        featureId: String? = null,
        bucket: Float? = null,
        stickyBucketUsed: Boolean? = null,
        attributeOverrides: Map<String, Any>

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
            context = gbContext,
            attr = experiment.hashAttribute,
            fallback = if (gbContext.stickyBucketService != null
                && experiment.disableStickyBucketing != true
            ) experiment.fallBackAttribute else null,
            attributeOverrides = attributeOverrides
        )
        val experimentMeta = experiment.meta ?: emptyList()
        val meta =
            if (experimentMeta.size > targetVariationIndex)
                experimentMeta[targetVariationIndex] else null

        return GBExperimentResult(
            inExperiment = inExperiment,
            variationId = targetVariationIndex,
            value = if (experiment.variations.size > targetVariationIndex)
                experiment.variations[targetVariationIndex]
            else mapOf(null to null).toJsonElement(),
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
