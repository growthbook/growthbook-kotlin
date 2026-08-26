package com.sdk.growthbook.utils

import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.serializable_model.SerializableGBContextualBandit
import com.sdk.growthbook.serializable_model.SerializableGBFeature
import com.sdk.growthbook.serializable_model.gbDeserialize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

@OptIn(ExperimentalEncodingApi::class)
fun decodeBase64(base64: String): ByteArray {
    return Base64.decode(base64)
}

fun encryptToFeaturesDataModel(string: String): GBFeatures? {
    val jsonParser = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

    return try {
        val serializableGBFeatures: Map<String, SerializableGBFeature> =
            jsonParser.decodeFromString(
                deserializer = MapSerializer(
                    String.serializer(),
                    SerializableGBFeature.serializer()
                ),
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
): GBFeatures? = try {
    val encryptedArrayData = encryptedString.split(".")

    val iv = decodeBase64(encryptedArrayData[0])
    val key = decodeBase64(encryptionKey)
    val stringToDecrypt = decodeBase64(encryptedArrayData[1])

    val cryptoLocal = subtleCrypto ?: DefaultCrypto()

    val encrypt: ByteArray = cryptoLocal.decrypt(stringToDecrypt, key, iv)
    val encryptString: String =
        encrypt.decodeToString()
    encryptToFeaturesDataModel(encryptString)
} catch (t: Throwable) {
    // Throwable, not Exception: a malformed payload (missing "." separator, non-base64 input) or a
    // rotated key throws out of split/decodeBase64/decrypt, and on the JS/wasm targets a WebCrypto
    // failure can surface as a plain Throwable. Returning null keeps the rest of the payload usable
    // instead of failing the whole fetch — see the per-field handling in FeaturePayloadDecoder.
    // Never log the blob or the key.
    GB.error(errorMessage = "Crypto: failed to decrypt features", throwable = t)
    null
}

fun getSavedGroupFromEncryptedSavedGroup(
    encryptedString: String,
    encryptionKey: String,
    subtleCrypto: Crypto? = null,
): JsonObject? = try {
    val encryptedArrayData = encryptedString.split(".")

    val iv = decodeBase64(encryptedArrayData[0])
    val key = decodeBase64(encryptionKey)
    val stringToDecrypt = decodeBase64(encryptedArrayData[1])

    val cryptoLocal = subtleCrypto ?: DefaultCrypto()

    val encrypt: ByteArray = cryptoLocal.decrypt(stringToDecrypt, key, iv)
    val encryptString: String =
        encrypt.decodeToString()

    Json.decodeFromString(JsonObject.serializer(), encryptString)
} catch (t: Throwable) {
    GB.error(errorMessage = "Crypto: failed to decrypt saved groups", throwable = t)
    null
}

fun getBanditsFromEncryptedBandits(
    encryptedString: String,
    encryptionKey: String,
    subtleCrypto: Crypto? = null,
): Map<String, GBContextualBandit>? = try {
    val parts = encryptedString.split(".")

    val iv = decodeBase64(parts[0])
    val key = decodeBase64(encryptionKey)
    val stringToDecrypt = decodeBase64(parts[1])

    val cryptoLocal = subtleCrypto ?: DefaultCrypto()

    val decrypted = cryptoLocal.decrypt(stringToDecrypt, key, iv).decodeToString()
    val jsonParser = Json { isLenient = true; ignoreUnknownKeys = true }
    jsonParser
        .decodeFromString(
            MapSerializer(String.serializer(), SerializableGBContextualBandit.serializer()),
            decrypted
        )
        .mapValues { it.value.gbDeserialize() }
} catch (t: Throwable) {
    GB.error(errorMessage = "Crypto: failed to decrypt contextual bandits", throwable = t)
    null
}
