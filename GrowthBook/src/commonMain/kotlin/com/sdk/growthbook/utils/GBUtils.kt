package com.sdk.growthbook.utils

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.evaluators.UserContext
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.StickyBucketAssignmentDocsType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.math.roundToInt
import com.sdk.growthbook.kotlinx.serialization.gbSerialize

/**
 * Fowler-Noll-Vo hash - 32 bit
 */
internal class FNV {

    private val INIT32 = BigInteger(0x811c9dc5)
    private val PRIME32 = BigInteger(0x01000193)
    private val MOD32 = BigInteger(2).pow(32)

    /**
     * Fowler-Noll-Vo hash - 32 bit
     * Returns BigInteger
     */
    fun fnv1a32(data: String): BigInteger {
        var hash: BigInteger = INIT32
        for (b in data) {
            hash = hash.xor(BigInteger(b.code and 0xff))
            hash = hash.multiply(PRIME32).mod(MOD32)
        }
        return hash
    }
}

/**
 * GrowthBook Utils Class
 * Contains Methods for
 * - hash
 * - inNameSpace
 * - getEqualWeights
 * - getBucketRanges
 * - chooseVariation
 * - getGBNameSpace
 * - inRange
 * - isFilteredOut
 * - isIncludedInRollout
 */
internal class GBUtils {
    companion object {

        /**
         * Hashes a string to a float between 0 and 1
         * fnv32a returns an integer, so we convert that to a float using a modulus:
         */
        fun hash(
            stringValue: String,
            hashVersion: Int?,
            seed: String?
        ): Float? {
            if (hashVersion == null) return null
            return when (hashVersion) {
                1 -> hashV1(stringValue, seed)
                2 -> hashV2(stringValue, seed)
                else -> null
            }
        }

        /**
         * Method for hash stings to float for hash version #1
         */
        private fun hashV1(stringValue: String, seed: String?): Float {
            val bigInt: BigInteger = FNV().fnv1a32(stringValue + seed)
            val thousand = BigInteger(1000)
            val remainder = bigInt.remainder(thousand)

            val remainderAsFloat = remainder.toString().toFloat()
            return remainderAsFloat / 1000f
        }

        /**
         * Method for hash stings to float for hash version #2
         */
        private fun hashV2(stringValue: String, seed: String?): Float {
            val first: BigInteger = FNV().fnv1a32(seed + stringValue)
            val second: BigInteger = FNV().fnv1a32(first.toString())

            val tenThousand = BigInteger(10000)
            val remainder = second.remainder(tenThousand)

            val remainderAsFloat = remainder.toString().toFloat()
            return remainderAsFloat / 10000f
        }

        /**
         * This checks if a userId is within an experiment namespace or not.
         */
        fun inNamespace(userId: String, namespace: GBNameSpace): Boolean {

            val hash = hash(
                stringValue = userId + "__",
                hashVersion = 1,
                seed = namespace.first
            ) ?: return false

            return inRange(
                n = hash,
                range = GBBucketRange(
                    first = namespace.second,
                    second = namespace.third
                )
            )
        }

        /**
         * Returns an array of floats with numVariations items that are all equal and sum to 1.
         * For example, getEqualWeights(2) would return [0.5, 0.5].
         */
        fun getEqualWeights(numVariations: Int): List<Float> {
            if (numVariations <= 0) return emptyList()
            val weight = 1.0f / numVariations.toFloat()
            return List(numVariations) { weight }
        }

        /**
         * This converts and experiment's coverage and variation weights
         * into an array of bucket ranges.
         */
        fun getBucketRanges(
            numVariations: Int,
            coverage: Float,
            weights: List<Float>?
        ): List<GBBucketRange> {
            val bucketRange: List<GBBucketRange>

            var targetCoverage = coverage

            // Clamp the value of coverage to between 0 and 1 inclusive.
            if (coverage < 0) targetCoverage = 0F
            if (coverage > 1) targetCoverage = 1F

            // Default to equal weights if the weights don't match the number of variations.
            val equal = getEqualWeights(numVariations)
            var targetWeights = weights ?: equal
            if (targetWeights.size != numVariations) {
                targetWeights = equal
            }

            // Default to equal weights if the sum is not equal 1 (
            // or close enough when rounding errors are factored in):
            val weightsSum = targetWeights.reduce { acc, fl -> acc + fl }
            if (weightsSum < 0.99 || weightsSum > 1.01) {
                targetWeights = getEqualWeights(numVariations)
            }

            // Convert weights to ranges and return
            var cumulative = 0f

            bucketRange = targetWeights.map { weight ->
                val start = cumulative
                cumulative += weight

                GBBucketRange(
                    start.roundTo(4),
                    (start + (targetCoverage * weight)).roundTo(4)
                )
            }

            return bucketRange
        }

        /**
         * Extension function for round float number to specific digit after comma
         */
        private fun Float.roundTo(numFractionDigits: Int): Float {
            val factor = 10F.pow(numFractionDigits.toFloat())
            return (this * factor).roundToInt() / factor
        }

        /**
         * Choose Variation from List of ranges which matches particular number
         */
        fun chooseVariation(n: Float, ranges: List<GBBucketRange>): Int {
            for ((index, range) in ranges.withIndex()) {
                if (inRange(n = n, range = range)) {
                    return index
                }
            }

            return -1
        }

        /**
         * Convert JsonArray to GBNameSpace
         */
        fun getGBNameSpace(namespace: JsonArray): GBNameSpace? {

            if (namespace.size >= 3) {
                val title = namespace[0].jsonPrimitive.contentOrNull
                val start = namespace[1].jsonPrimitive.floatOrNull
                val end = namespace[2].jsonPrimitive.floatOrNull

                if (title != null && start != null && end != null) {
                    return GBNameSpace(title, start, end)
                }
            }

            return null
        }

        /**
         * This function can be used to help with the evaluation of the version string comparison
         */
        fun paddedVersionString(input: String): String {
            // "v1.2.3-rc.1+build123" -> ["1","2","3","rc","1"]
            var parts: List<String> = input.replace(Regex("^v|\\+.*\$"), "")
                .split(Regex("[-.]"))

            // ["1","0","0"] -> ["1","0","0","~"]
            // "~" is the largest ASCII character, so this will make "1.0.0" greater
            // than "1.0.0-beta" for example
            if (parts.size == 3) {
                val arrayList = ArrayList(parts)
                arrayList.add("~")
                parts = arrayList
            }

            // Left pad each numeric part with spaces
            // so string comparisons will work ("9">"10", but " 9"<"10")
            // Then, join back together into a single string
            return parts.joinToString("-") {
                if (it.matches(Regex("^\\d+$"))) it.padStart(5, ' ') else it
            }
        }

        /**
         * Determines if a number n is within the provided range.
         */
        private fun inRange(
            n: Float?,
            range: GBBucketRange?
        ): Boolean {
            return if (n == null || range == null) false
            else n >= range.first && n < range.second
        }

        /**
         * This is a helper method to evaluate filters for both feature flags and experiments.
         */
        fun isFilteredOut(
            filters: List<GBFilter>?,
            attributeOverrides: Map<String, GBValue>?,
            evaluationContext: EvaluationContext,

            ): Boolean {
            if (filters == null) return false
            if (attributeOverrides == null) return false

            return filters.any { filter: GBFilter ->
                val hashAttribute: String = filter.attribute ?: "id"

                val hashValueElement: GBValue =
                    evaluationContext.userContext
                        .attributes.getValue(hashAttribute)

                if (hashValueElement is GBValue.Unknown) return@any true
                if (hashValueElement.gbSerialize() !is JsonPrimitive) return@any true

                val hashValue: String = hashValueElement.toHashValue()

                if (hashValue.isEmpty()) return@any true
                val hashVersion: Int = filter.hashVersion ?: 2

                val n: Float = hash(
                    hashValue,
                    hashVersion,
                    filter.seed
                ) ?: return@any true
                val ranges: List<GBBucketRange> = filter.ranges
                ranges.none { range: GBBucketRange? ->
                    inRange(
                        n,
                        range
                    )
                }
            }
        }

        /**
         * Determines if the user is part of a gradual feature rollout.
         */
        fun isIncludedInRollout(
            attributes: Map<String, GBValue>,
            attributeOverrides: Map<String, GBValue>,
            seed: String?,
            hashAttribute: String?,
            fallbackAttribute: String?,
            range: GBBucketRange?,
            coverage: Float?,
            hashVersion: Int?,
        ): Boolean {
            if (range == null && coverage == null) return true

            val (_, hashValue) = getHashAttribute(
                attr = hashAttribute,
                fallback = fallbackAttribute,
                attributes = attributes,
                attributeOverrides = attributeOverrides,
            )

            val hash = hash(
                seed = seed,
                stringValue = hashValue,
                hashVersion = hashVersion ?: 1
            )
                ?: return false

            return if (range != null) {
                inRange(n = hash, range = range)
            } else if (coverage != null) {
                hash <= coverage
            } else {
                true
            }
        }

        /**
         * Method that get cached assignments
         * and set it to Context's Sticky Bucket Assignments documents
         */
        fun refreshStickyBuckets(
            context: GBContext,
            data: FeaturesDataModel?,
            attributeOverrides: Map<String, GBValue>
        ) {
            val stickyBucketService = context.stickyBucketService ?: return

            val attributes = getStickyBucketAttributes(
                context = context,
                data = data,
                attributeOverrides = attributeOverrides
            )

            with(
                StickyBucketServiceHelper(stickyBucketService)
            ) {
                getAllAssignments(
                    attributes = attributes,
                    onResult = {
                        context.stickyBucketAssignmentDocs = it
                    }
                )
            }
        }

        /**
         * Supportive method for get attribute value from Context
         */
        private fun getStickyBucketAttributes(
            context: GBContext,
            data: FeaturesDataModel?,
            attributeOverrides: Map<String, GBValue>
        ): Map<String, String> {
            val attributes = mutableMapOf<String, String>()
            context.stickyBucketIdentifierAttributes =
                deriveStickyBucketIdentifierAttributes(context, data)

            context.stickyBucketIdentifierAttributes?.forEach { attr ->
                val hashValue =
                    getHashAttribute(
                        attr = attr,
                        attributes = context.attributes,
                        attributeOverrides = attributeOverrides
                    )
                attributes[attr] = hashValue.second
            }
            return attributes
        }

        /**
         * Supportive method for get attribute value from Context if identifiers missed
         */
        private fun deriveStickyBucketIdentifierAttributes(
            context: GBContext,
            data: FeaturesDataModel?
        ): List<String> {
            val attributes = mutableSetOf<String>()
            val features = data?.features ?: context.features

            features.keys.forEach { id ->
                val feature = features[id]
                feature?.rules?.forEach { rule ->
                    rule.variations?.let { _ ->
                        attributes.add(rule.hashAttribute ?: "id")
                        rule.fallbackAttribute?.let { fallbackAttribute ->
                            attributes.add(fallbackAttribute)
                        }
                    }
                }
            }
            return attributes.toList()
        }

        /**
         * Method to get actual Sticky Bucket assignments.
         * Also this method handles if assignments belong to user
         */
        private fun getStickyBucketAssignments(
            userContext: UserContext,
            expHashAttribute: String?,
            expFallBackAttribute: String?,
            attributes: Map<String, GBValue>,
            attributeOverrides: Map<String, GBValue>
        ): Map<String, String> {

            val mergedAssignments = mutableMapOf<String, String>()
            val stickyBucketAssignmentDocs =
                userContext.stickyBucketAssignmentDocs ?: return mergedAssignments

            val (hashAttribute, hashValue) = getHashAttribute(
                attr = expHashAttribute,
                fallback = null,
                attributes = attributes,
                attributeOverrides = attributeOverrides
            )

            val hashKey = "$hashAttribute||$hashValue"

            val (fallbackAttribute, fallbackValue) = getHashAttribute(
                attr = null,
                fallback = expFallBackAttribute,
                attributes = attributes,
                attributeOverrides = attributeOverrides
            )

            val fallbackKey = if (fallbackValue.isEmpty()) null
            else "$fallbackAttribute||$fallbackValue"

            val userFallbackAttribute = userContext.attributes[expFallBackAttribute]
            if (userFallbackAttribute != null) {
                val leftOperand =
                    if (stickyBucketAssignmentDocs[expFallBackAttribute + "||" + userFallbackAttribute.toHashValue()] == null
                    ) {
                        null
                    } else {
                        stickyBucketAssignmentDocs[expFallBackAttribute + "||" + userFallbackAttribute.toHashValue()]?.attributeValue
                    }

                if (leftOperand != userFallbackAttribute.toHashValue()) {
                    userContext.stickyBucketAssignmentDocs = emptyMap()
                }
            }

            if (userContext.stickyBucketAssignmentDocs != null) {
                userContext.stickyBucketAssignmentDocs!!.values.forEach { docs ->
                    mergedAssignments.putAll(
                        docs.assignments
                    )
                }
            }

            fallbackKey?.let { fallback ->
                stickyBucketAssignmentDocs[fallback]?.let { fallbackAssignments ->
                    mergedAssignments.putAll(fallbackAssignments.assignments)
                }
            }

            stickyBucketAssignmentDocs[hashKey]?.let { hashAssignments ->
                mergedAssignments.putAll(hashAssignments.assignments)
            }

            return mergedAssignments
        }

        /**
         * Method to get Sticky Bucket variations
         */
        fun getStickyBucketVariation(
            experimentKey: String,
            userContext: UserContext,
            experimentBucketVersion: Int = 0,
            minExperimentBucketVersion: Int = 0,
            meta: List<GBVariationMeta> = emptyList(),
            expFallBackAttribute: String? = null,
            expHashAttribute: String? = "id",
            attributeOverrides: Map<String, GBValue>
        ): Pair<Int, Boolean?> {
            val id = getStickyBucketExperimentKey(experimentKey, experimentBucketVersion)
            val assignments = getStickyBucketAssignments(
                userContext = userContext,
                expHashAttribute = expHashAttribute,
                expFallBackAttribute = expFallBackAttribute,
                attributes = userContext.attributes,
                attributeOverrides = attributeOverrides,
            )

            if (minExperimentBucketVersion > 0) {
                // users with any blocked bucket version (0 to minExperimentBucketVersion - 1) are excluded from the test
                for (version in 0 until minExperimentBucketVersion) {
                    val blockedKey = getStickyBucketExperimentKey(experimentKey, version)
                    if (blockedKey in assignments) {
                        return Pair(-1, true)
                    }
                }
            }
            val variationKey = assignments[id] ?: return Pair(-1, null)
            val variation = meta.indexOfFirst { it.key == variationKey }
            return if (0 <= variation) {
                Pair(variation, null)
            } else {
                Pair(-1, null)
            }
        }

        /**
         * Method to get Experiment key from cache
         */
        fun getStickyBucketExperimentKey(
            experimentKey: String,
            experimentBucketVersion: Int = 0
        ): String {
            return "${experimentKey}__${experimentBucketVersion}"
        }

        /**
         * Method for generate Sticky Bucket Assignment document
         */
        fun generateStickyBucketAssignmentDoc(
            attributeName: String,
            attributeValue: String,
            assignments: Map<String, String>,
            stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType?,
        ): Triple<String, GBStickyAssignmentsDocument, Boolean> {

            val key = "$attributeName||$attributeValue"

            val existingAssignments =
                stickyBucketAssignmentDocs?.get(key)?.assignments ?: emptyMap()

            val newAssignments =
                existingAssignments.toMutableMap().apply {
                    putAll(assignments)
                }

            val changed = existingAssignments != newAssignments

            return Triple(
                key,
                GBStickyAssignmentsDocument(
                    attributeName = attributeName,
                    attributeValue = attributeValue,
                    assignments = newAssignments
                ),
                changed
            )
        }

        /**
         * Method for get hash value by identifier
         */
        fun getHashAttribute(
            attr: String?,
            fallback: String? = null,
            attributes: Map<String, GBValue>,
            attributeOverrides: Map<String, GBValue>
        ): Pair<String, String> {
            var hashAttribute = attr ?: "id"
            var hashValue = ""

            if (attributeOverrides[hashAttribute] != null) {
                // hashValue = attributeOverrides[hashAttribute].toString()
                hashValue = attributeOverrides[hashAttribute].toHashValue()
            } else if (attributes[hashAttribute] != null) {
                hashValue = attributes[hashAttribute].toHashValue()
            }

            // if no match, try fallback
            if (hashValue.isEmpty() && fallback != null) {
                if (attributeOverrides[fallback] != null) {
                    hashValue = attributeOverrides[fallback].toHashValue()
                } else if (attributes[fallback] != null) {
                    hashValue = attributes[fallback].toHashValue()
                }

                if (hashValue.isNotEmpty()) {
                    hashAttribute = fallback
                }
            }

            return Pair(hashAttribute, hashValue)
        }

        private fun GBValue?.toHashValue(): String =
            when (this) {
                is GBString -> this.value // without quotes
                else -> this?.gbSerialize().toString()
            }
    }
}
