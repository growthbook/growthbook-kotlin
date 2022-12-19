package com.sdk.growthbook.Utils

import com.google.gson.Gson
import com.sdk.growthbook.features.FeaturesDataModel
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
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

class DefaultCrypto(private val algorithm: String = "AES") : Crypto {

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

fun secretKeyToString(secretKey: SecretKey): String? {
    return Base64.getEncoder().encodeToString(secretKey.encoded)
}

fun stringToSecretKey(encodedKey: String, algorithm: String = "AES"): SecretKeySpec {
    val decodedKey: ByteArray = encodedKey.toByteArray()
    return SecretKeySpec(decodedKey, 0, decodedKey.size, algorithm)
}

fun ivToString(iv: IvParameterSpec): String {
    return String(iv.iv)
}

fun stringToIv(iv: String): IvParameterSpec{
    val decodedIv: ByteArray = Base64.getDecoder().decode(iv)
    return IvParameterSpec(decodedIv)
}

fun encryptToFeaturesDataModel(string: String): FeaturesDataModel? {
    val gson = Gson()
    val json = gson.toJson(string)
    return try {
        gson.fromJson(json, FeaturesDataModel::class.java)
    } catch (e: Exception) {
        null
    }
}
