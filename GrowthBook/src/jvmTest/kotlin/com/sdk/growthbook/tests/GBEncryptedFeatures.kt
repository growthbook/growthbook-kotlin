package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

class GBEncryptedFeatures {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testEncryptDecrypt() {
        val testCases = GBTestHelper.getDecryptData()
        testCases.forEach { jsonElement ->
            try {
                val test: JsonArray = jsonElement as JsonArray

                val ivString = test[1].jsonPrimitive.content
                val keyString = test[2].jsonPrimitive.content
                val stringForEncrypt = test[3].jsonPrimitive.content

                val decodedIv = Base64.decode(ivString.split('.').first())
                val decodedKey = Base64.decode(keyString)

                val defaultCrypto = DefaultCrypto()

                val encryptedValue = defaultCrypto.encrypt(
                    stringForEncrypt.toByteArray(),
                    decodedKey,
                    decodedIv
                )
                val decryptedValue = defaultCrypto.decrypt(
                    encryptedValue,
                    decodedKey,
                    decodedIv
                )
                println(stringForEncrypt)
                println(String(decryptedValue))
                assertEquals(stringForEncrypt, String(decryptedValue))
            } catch (e: Exception) {
                println(e.message)
            }
        }
    }

    private val testApiKey = "4r23r324f23"
    private val testHostURL = "https://host.com"
    private val testAttributes: HashMap<String, Any> = HashMap()

    @Test
    fun testEncrypt() {

        val sdkInstance = GBSDKBuilder(
            testApiKey,
            testHostURL,
            attributes = testAttributes,
            //TODO
            encryptionKey = "",
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
            ).initialize()

        val keyString = "Ns04T5n9+59rl2x3SlNHtQ=="
        val encryptedFeatures =
            "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"
        val expectedResult =
            "{\"testfeature1\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":\"1234\"},\"force\":false}]}}"

        sdkInstance.setEncryptedFeatures(encryptedFeatures, keyString, null)

        val features = encryptToFeaturesDataModel(expectedResult)!!
        println(features)
        assertEquals(
            features["testfeature1"]?.rules?.get(0)?.condition,
            sdkInstance.getGBContext().features["testfeature1"]?.rules?.get(0)?.condition
        )
        assertEquals(
            features["testfeature1"]?.rules?.get(0)?.force,
            sdkInstance.getGBContext().features["testfeature1"]?.rules?.get(0)?.force
        )
    }

    @Test
    fun testDecrypt() {
    }
}