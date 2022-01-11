package com.sdk.growthbook.Utils

import com.ionspin.kotlin.bignum.integer.BigInteger

/*
    Fowler-Noll-Vo hash - 32 bit
 */
internal class FNV {
    private val INIT32 = BigInteger(0x811c9dc5)
    private val PRIME32 = BigInteger(0x01000193)
    private val MOD32 = BigInteger(2).pow(32)

    /// Fowler-Noll-Vo hash - 32 bit
    fun fnv1a_32(data: String): BigInteger? {
        var hash: BigInteger = INIT32
        for (b in data) {
            hash = hash.xor(BigInteger(b.code and 0xff))
            hash = hash.multiply(PRIME32).mod(MOD32)
        }
        return hash
    }
}