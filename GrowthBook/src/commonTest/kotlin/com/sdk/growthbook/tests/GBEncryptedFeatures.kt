package com.sdk.growthbook.tests

import com.sdk.growthbook.Utils.DefaultCrypto
import com.sdk.growthbook.Utils.stringToIv
import com.sdk.growthbook.Utils.stringToSecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class GBEncryptedFeatures {

    @Test
    fun testDefaultEncryption(){
        val keyString = "Ns04T5n9+59rl2x3SlNHtQ=="
        val stringForEncrypt = "{\"testfeature1\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":\"1234\"},\"force\":false}]}}"
        val ivString = "vMSg2Bj/IurObDsWVmvkUg=="
        val key =  SecretKeySpec(keyString.toByteArray(), 0, keyString.length, "AES")
        val iv = IvParameterSpec(ivString.toByteArray())

        val defaultCrypto = DefaultCrypto()
        val encryptedValue = defaultCrypto.encrypt(
            stringForEncrypt,
            key,
            iv
        )
        val decryptedValue = defaultCrypto.decrypt(
            encryptedValue,
            key,
            iv
        )

        assertEquals(stringForEncrypt, decryptedValue)
    }
}