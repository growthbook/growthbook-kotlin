package com.sdk.growthbook.tests

import com.google.gson.Gson
import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.Utils.DefaultCrypto
import com.sdk.growthbook.Utils.encryptToFeaturesDataModel
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import kotlinx.coroutines.DelicateCoroutinesApi
import java.util.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class GBEncryptedFeatures {

    @Test
    fun testEncryptDecrypt() {
        val keyString = "Ns04" +
            "T5n9" +
            "+59r" +
            "l2x3" +
            "SlNH" +
            "tQ=="
        val stringForEncrypt =
            "{\"testfeature1\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":\"1234\"},\"force\":false}]}}"
        val ivString = "vMSg2Bj/IurObDsWVmvkUg=="
        val decodedIv = Base64.getDecoder().decode(ivString)
        val decodedKey = Base64.getDecoder().decode(keyString)

        val key = SecretKeySpec(decodedKey, "AES")

        val iv = IvParameterSpec(
            decodedIv
        )

        // assertEquals(24, key.encoded.size)

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

    val testApiKey = "4r23r324f23"
    val testHostURL = "https://host.com"
    val testAttributes: HashMap<String, Any> = HashMap()

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testEncrypt() {

        val sdkInstance = GBSDKBuilderApp(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            trackingCallback = { gbExperiment: GBExperiment, gbExperimentResult: GBExperimentResult ->

            }).initialize()

        val keyString = "Ns04T5n9+59rl2x3SlNHtQ=="
        val encryptedFeatures =
            "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"
        val expectedResult =
            "{\"testfeature1\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":\"1234\"},\"force\":false}]}}"

        sdkInstance.setEncryptedFeatures(encryptedFeatures, keyString, null)

        val features = encryptToFeaturesDataModel(expectedResult)!!


        assertEquals(
            features["testfeature1"]?.rules?.get(0)?.condition,
            sdkInstance.getGBContext().features["testfeature1"]?.rules?.get(0)?.condition
        )
        assertEquals(
            features["testfeature1"]?.rules?.get(0)?.force,
            sdkInstance.getGBContext().features["testfeature1"]?.rules?.get(0)?.force
        )
    }
}