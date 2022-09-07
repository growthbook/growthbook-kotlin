package com.sdk.growthbook.utils

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.floatOrNull
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
    fun fnv1a_32(data: String): BigInteger? {
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
 */
internal class GBUtils {
    companion object {

        /**
         * Hashes a string to a float between 0 and 1
         * fnv32a returns an integer, so we convert that to a float using a modulus:
         */
        fun hash(data: String) : Float? {
            val hash = FNV().fnv1a_32(data)
            val remainder = hash?.remainder(BigInteger(1000))
            val value = remainder?.toString()?.toFloatOrNull()?.div(1000f)
            return value
        }

        /**
         * This checks if a userId is within an experiment namespace or not.
         */
        fun inNamespace(userId: String, namespace: GBNameSpace): Boolean {

            val hash = hash(userId + "__" + namespace.first)

            if (hash != null) {
                return hash >= namespace.second && hash < namespace.third
            }

            return false
        }

        /**
         * Returns an array of floats with numVariations items that are all equal and sum to 1. For example, getEqualWeights(2) would return [0.5, 0.5].
         */
        fun getEqualWeights(numVariations: Int): List<Float> {
            var weights : List<Float> = ArrayList()

            if (numVariations >= 1) {
                weights = List(numVariations){1.0f / (numVariations)}
            }

            return weights
        }

        /**
         * This converts and experiment's coverage and variation weights into an array of bucket ranges.
         */
        fun getBucketRanges(numVariations: Int, coverage: Float, weights: List<Float>): List<GBBucketRange> {
            var bucketRange : List<GBBucketRange>

            var targetCoverage = coverage

            // Clamp the value of coverage to between 0 and 1 inclusive.
            if (coverage < 0) targetCoverage = 0F
            if (coverage > 1) targetCoverage = 1F


            // Default to equal weights if the weights don't match the number of variations.
            var targetWeights = weights
            if (weights.size != numVariations) {
                targetWeights = getEqualWeights(numVariations);
            }

            // Default to equal weights if the sum is not equal 1 (or close enough when rounding errors are factored in):
            val weightsSum = targetWeights.sum()
            if (weightsSum < 0.99 || weightsSum > 1.01) {
                targetWeights = getEqualWeights(numVariations);
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
        fun chooseVariation(n: Float, ranges: List<GBBucketRange>) : Int {

            var counter = 0
            for (range in ranges) {
                if (n >= range.first && n < range.second) {
                    return counter
                }
                counter++
            }

            return -1
        }

        /**
         * Convert JsonArray to GBNameSpace
         */
        fun getGBNameSpace(namespace: JsonArray) : GBNameSpace? {

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

    }
}