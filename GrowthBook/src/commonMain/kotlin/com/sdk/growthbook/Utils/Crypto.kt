package com.sdk.growthbook.Utils

import com.soywiz.krypto.AES
import com.soywiz.krypto.Padding
import com.soywiz.krypto.encoding.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface Crypto {
    fun decrypt(
        cipherText: ByteArray,
        key: ByteArray,
        iv: ByteArray,

        ): ByteArray

    fun encrypt(
        inputText: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray
}

class DefaultCrypto : Crypto {

    private val padding = Padding.PKCS7Padding

    override fun decrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return AES.decryptAesCbc(cipherText, key, iv, padding)
    }

    override fun encrypt(inputText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return AES.encryptAesCbc(inputText, key, iv, padding)
    }
}

fun decodeBase64(base64: String): ByteArray {
    return Base64.decode(base64)
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

fun getFeaturesFromEncryptedFeatures(
    encryptedString: String,
    encryptionKey: String,
    subtleCrypto: Crypto?
): GBFeatures? {
    val encryptedArrayData = encryptedString.split(".")

    val iv = decodeBase64(encryptedArrayData[0])
    val key = decodeBase64(encryptionKey)
    val stringToDecrypt = decodeBase64(encryptedArrayData[1])

    val cryptoLocal = subtleCrypto ?: DefaultCrypto()

    val encrypt: ByteArray = cryptoLocal.decrypt(stringToDecrypt, key, iv)
    val encryptString: String =
        encrypt.decodeToString()
    return encryptToFeaturesDataModel(encryptString)
}
