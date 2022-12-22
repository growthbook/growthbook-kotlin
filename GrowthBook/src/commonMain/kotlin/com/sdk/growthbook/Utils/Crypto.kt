package com.sdk.growthbook.Utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

interface Crypto {
    fun decrypt(
        cipherText: String,
        key: SecretKeySpec,
        iv: IvParameterSpec,

        ): String

    fun encrypt(
        inputText: String,
        key: SecretKeySpec,
        iv: IvParameterSpec,
    ): String
}

class DefaultCrypto(private val algorithm: String = "AES/CBC/PKCS5Padding") : Crypto {

    override fun decrypt(
        cipherText: String,
        key: SecretKeySpec,
        iv: IvParameterSpec,
    ): String {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val plainText = cipher.doFinal(Base64.getDecoder().decode(cipherText))
        return String(plainText)
    }

    override fun encrypt(
        inputText: String,
        key: SecretKeySpec,
        iv: IvParameterSpec,
    ): String {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val cipherText = cipher.doFinal(inputText.toByteArray())
        return Base64.getEncoder().encodeToString(cipherText)
    }
}

fun stringToSecretKey(encodedKey: String, algorithm: String = "AES"): SecretKeySpec {
    val decodedKey: ByteArray = Base64.getDecoder().decode(encodedKey)
    return SecretKeySpec(decodedKey, algorithm)
}

fun stringToIv(iv: String): IvParameterSpec {
    val decodedIv: ByteArray = Base64.getDecoder().decode(iv)
    return IvParameterSpec(decodedIv)
}

fun encryptToFeaturesDataModel(string: String): GBFeatures? {
    val JSONParser = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    return try {

        val result: GBFeatures = JSONParser.decodeFromString(string)
        result
    } catch (e: Exception) {
        null
    }
}
