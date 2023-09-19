package com.sdk.growthbook.Utils

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.math.roundToInt

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

        private fun hashV1(stringValue: String, seed: String?): Float {
            val bigInt: BigInteger = FNV().fnv1a32(stringValue + seed)
            val thousand = BigInteger(1000)
            val remainder = bigInt.remainder(thousand)

            val remainderAsFloat = remainder.toString().toFloat()
            return remainderAsFloat / 1000f
        }

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

            val hash = hash(userId, 1, "__" + namespace.first)

            if (hash != null) {
                return hash >= namespace.second && hash < namespace.third
            }

            return false
        }

        /**
         * Returns an array of floats with numVariations items that are all equal and sum to 1. For example, getEqualWeights(2) would return [0.5, 0.5].
         */
        fun getEqualWeights(numVariations: Int): List<Float> {
            var weights: List<Float> = ArrayList()

            if (numVariations >= 1) {
                weights = List(numVariations) { 1.0f / (numVariations) }
            }

            return weights
        }

        /**
         * This converts and experiment's coverage and variation weights into an array of bucket ranges.
         */
        fun getBucketRanges(
            numVariations: Int,
            coverage: Float,
            weights: List<Float>
        ): List<GBBucketRange> {
            val bucketRange: List<GBBucketRange>

            var targetCoverage = coverage

            // Clamp the value of coverage to between 0 and 1 inclusive.
            if (coverage < 0) targetCoverage = 0F
            if (coverage > 1) targetCoverage = 1F

            // Default to equal weights if the weights don't match the number of variations.
            var targetWeights = weights
            if (weights.size != numVariations) {
                targetWeights = getEqualWeights(numVariations)
            }

            // Default to equal weights if the sum is not equal 1 (or close enough when rounding errors are factored in):
            val weightsSum = targetWeights.sum()
            if (weightsSum < 0.99 || weightsSum > 1.01) {
                targetWeights = getEqualWeights(numVariations)
            }

            // Convert weights to ranges and return
            var cumulative = 0f

            bucketRange = targetWeights.map { weight ->
                val start = cumulative
                cumulative += weight

                GBBucketRange(start.roundTo(4), (start + (targetCoverage * weight)).roundTo(4))
            }

            return bucketRange
        }

        private fun Float.roundTo(numFractionDigits: Int): Float {
            val factor = 10F.pow(numFractionDigits.toFloat())
            return (this * factor).roundToInt() / factor
        }

        /**
         * Choose Variation from List of ranges which matches particular number
         */
        fun chooseVariation(n: Float, ranges: List<GBBucketRange>): Int {

            for ((counter, range) in ranges.withIndex()) {
                if (n >= range.first && n < range.second) {
                    return counter
                }
            }

            return -1
        }

        /**
         * Convert JsonArray to GBNameSpace
         */
        fun getGBNameSpace(namespace: JsonArray): GBNameSpace? {

            if (namespace.size >= 3) {
                val title = namespace[0].jsonPrimitive.content
                val start = namespace[1].jsonPrimitive.floatOrNull
                val end = namespace[2].jsonPrimitive.floatOrNull

                if (start != null && end != null) {
                    return GBNameSpace(title, start, end)
                }
            }

            return null
        }

        fun paddedVersionString(input: String): String {
            // "v1.2.3-rc.1+build123" -> ["1","2","3","rc","1"]
            var parts: List<String> = input.replace(Regex("^v|\\+.*\$"), "")
                .split(Regex("[-.]"))

            // ["1","0","0"] -> ["1","0","0","~"]
            // "~" is the largest ASCII character, so this will make "1.0.0" greater than "1.0.0-beta" for example
            if (parts.size == 3) {
                val arrayList = ArrayList(parts)
                arrayList.add("~")
                parts = arrayList
            }

            // Left pad each numeric part with spaces so string comparisons will work ("9">"10", but " 9"<"10")
            // Then, join back together into a single string
            return parts.joinToString("-") {
                if (it.matches(Regex("^\\d+$"))) it.padStart(5, ' ') else it
            }
        }

        /**
         * Determines if a number n is within the provided range.
         */
        fun inRange(
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
            attributes: JsonElement?
        ): Boolean {
            if (filters == null) return false
            if (attributes == null) return false

            return filters.any { filter: GBFilter ->
                val hashAttribute: String = filter.attribute ?: "id"

                val hashValueElement: JsonElement = attributes.jsonObject.getValue(hashAttribute)

                if (hashValueElement is JsonNull) return@any true
                if (hashValueElement !is JsonPrimitive) return@any true

                val hashValuePrimitive: JsonPrimitive = hashValueElement.jsonPrimitive
                val hashValue: String = hashValuePrimitive.toString()

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
            attributes: JsonElement?,
            seed: String?,
            hashAttribute: String?,
            range: GBBucketRange?,
            coverage: Float?,
            hashVersion: Int?
        ): Boolean {
            var latestHashAttribute = hashAttribute
            var latestHashVersion = hashVersion

            if (range == null && coverage == null) return true

            if (hashAttribute == null || hashAttribute == "") {
                latestHashAttribute = "id"
            }

            if (attributes == null) return false

            val hashValueElement: JsonElement = attributes.jsonObject.getValue(latestHashAttribute!!)
            if (hashValueElement is JsonNull) return false

            if (hashVersion == null) {
                latestHashVersion = 1
            }
            val hashValue: String = hashValueElement.toString()
            val hash: Float = hash(hashValue, latestHashVersion, seed) ?: return false

            return if (range != null) inRange(
                hash,
                range
            ) else hash <= coverage!!
        }
    }
}