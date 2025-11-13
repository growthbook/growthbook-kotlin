package com.sdk.growthbook.utils

import com.sdk.growthbook.serializable_model.SerializableGBFeature
import com.sdk.growthbook.serializable_model.gbDeserialize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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

@OptIn(DelicateCryptographyApi::class)
class DefaultCrypto : Crypto {

    private fun getCipher(key: ByteArray) = CryptographyProvider.Default
        .get(AES.CBC)
        .keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        .cipher(padding = true)

    override fun decrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return getCipher(key).decryptWithIvBlocking(iv, cipherText)
    }

    override fun encrypt(inputText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return getCipher(key).encryptWithIvBlocking(iv, inputText)
    }
}

// Platform-specific Base64 decoding
internal expect fun decodeBase64(base64: String): ByteArray

fun encryptToFeaturesDataModel(string: String): GBFeatures? {
    val jsonParser = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    return try {
        val serializableGBFeatures: Map<String, SerializableGBFeature> = jsonParser.decodeFromString(
            deserializer = MapSerializer(String.serializer(), SerializableGBFeature.serializer()),
            string = string
        )
        serializableGBFeatures.mapValues { it.value.gbDeserialize() }
    } catch (e: Exception) {
        null
    }
}

fun getFeaturesFromEncryptedFeatures(
    encryptedString: String,
    encryptionKey: String,
    subtleCrypto: Crypto? = null,
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

fun getSavedGroupFromEncryptedSavedGroup(
    encryptedString: String,
    encryptionKey: String,
    subtleCrypto: Crypto? = null,
): JsonObject? {
    val encryptedArrayData = encryptedString.split(".")

    val iv = decodeBase64(encryptedArrayData[0])
    val key = decodeBase64(encryptionKey)
    val stringToDecrypt = decodeBase64(encryptedArrayData[1])

    val cryptoLocal = subtleCrypto ?: DefaultCrypto()

    val encrypt: ByteArray = cryptoLocal.decrypt(stringToDecrypt, key, iv)
    val encryptString: String =
        encrypt.decodeToString()

    return try {
        Json.decodeFromString(JsonObject.serializer(), encryptString)
    } catch (e : Exception) {
        null
    }
}
