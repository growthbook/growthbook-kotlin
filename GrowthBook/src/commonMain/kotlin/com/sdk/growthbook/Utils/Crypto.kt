package com.sdk.growthbook.Utils

import com.soywiz.krypto.AES
import com.soywiz.krypto.CipherPadding
import com.soywiz.krypto.encoding.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface Crypto {
    fun decrypt(
        cipherText: ByteArray,
        key: ByteArray,
        iv: ByteArray,

        ): String

    fun encrypt(
        inputText: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): String
}

class DefaultCrypto(private val algorithm: String = "AES/CBC/PKCS5Padding") : Crypto {

    // override fun decrypt(
    //     cipherText: String,
    //     key: SecretKeySpec,
    //     iv: IvParameterSpec,
    // ): String {
    //     val text = Base64.decode(cipherText)
    //     val cipher = Cipher.getInstance(algorithm)
    //     cipher.init(Cipher.DECRYPT_MODE, key, iv)
    //     val plainText = cipher.doFinal(Base64.getDecoder().decode(cipherText))
    //     return String(
    //         AES.decryptAesCbc(
    //             text,
    //
    //             )
    //     )
    // }
    //
    // override fun encrypt(
    //     inputText: String,
    //     key: SecretKeySpec,
    //     iv: IvParameterSpec,
    // ): String {
    //     val cipher = Cipher.getInstance(algorithm)
    //     cipher.init(Cipher.ENCRYPT_MODE, key, iv)
    //     val cipherText = cipher.doFinal(inputText.toByteArray())
    //     return Base64.getEncoder().encodeToString(cipherText)
    // }

    override fun decrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): String {
        return String( AES.decryptAesCbc(cipherText, key, iv, CipherPadding.PKCS7Padding))
    }

    override fun encrypt(inputText: ByteArray, key: ByteArray, iv: ByteArray): String {
        return String( AES.encryptAesCbc(inputText, key, iv, CipherPadding.PKCS7Padding))
    }
}

// fun stringToSecretKey(encodedKey: String, algorithm: String = "AES"): SecretKeySpec {
//     val decodedKey: ByteArray = Base64.decode(encodedKey)
//     return SecretKeySpec(decodedKey, algorithm)
// }

fun decodeBase64(base64: String): ByteArray{
    return Base64.decode(base64)
}

// fun stringToIv(iv: String): String {
//     val decodedIv: ByteArray = Base64.decode(iv)
//     return de
// }

fun encryptToFeaturesDataModel(string: String): GBFeatures? {
    val JSONParser = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    return try {

        val result: GBFeatures = JSONParser.decodeFromString(string)
        result
    } catch (e: Exception) {
        null
    }
}
