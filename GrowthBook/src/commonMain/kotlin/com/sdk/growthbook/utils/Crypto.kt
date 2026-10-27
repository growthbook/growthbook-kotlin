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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-CBC primitives behind GrowthBook's encrypted payloads. [DefaultCrypto] is used unless a
 * custom implementation is passed in — via [com.sdk.growthbook.GrowthBookSDK.setEncryptedFeatures]
 * or the `subtleCrypto` parameter of the decrypt helpers below — which is how a host plugs in a
 * platform crypto provider (WebCrypto, a keystore-backed cipher) instead of the bundled one.
 *
 * Implementations are called from coroutines and must be safe to use from any thread. They may
 * throw on bad input: every SDK-side caller treats a failure as "this field is unusable", never as
 * a reason to fail the whole payload.
 */
interface Crypto {
    /**
     * Decrypts [cipherText] with AES-CBC (PKCS#7 padding) under [key] and [iv], both raw bytes —
     * the payload's own base64 decoding is the caller's job.
     */
    fun decrypt(
        cipherText: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray

    /**
     * The inverse of [decrypt]. Present for symmetry and for tests that build encrypted fixtures;
     * the SDK itself only ever decrypts.
     */
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

/**
 * Parses an already-decrypted feature map (`{"feature-key": {...}}`) into [GBFeatures], or null
 * when it cannot be parsed. Despite the name it decrypts nothing — it is the JSON step that
 * follows decryption, kept public because [com.sdk.growthbook.GrowthBookSDK.setEncryptedFeatures]
 * and the helpers below share it.
 */
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

/**
 * Decrypts an `encryptedFeatures` payload field and decodes it into [GBFeatures].
 *
 * [encryptedString] is the API's `iv.ciphertext` form, both halves base64; [encryptionKey] is the
 * base64 key from the SDK connection. Pass [subtleCrypto] to decrypt with your own [Crypto].
 *
 * @return the decoded features, or null when decryption or parsing fails — a rotated key, a
 *   malformed blob. Failing soft is deliberate: the rest of the payload stays usable, and the
 *   failure is logged without the blob or the key, neither of which may ever reach the log.
 */
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
} catch (c: CancellationException) {
    // These decrypts run inside coroutines; swallowing cancellation would break it.
    throw c
} catch (t: Throwable) {
    // Throwable, not Exception: a malformed payload (missing "." separator, non-base64 input) or a
    // rotated key throws out of split/decodeBase64/decrypt, and on the JS/wasm targets a WebCrypto
    // failure can surface as a plain Throwable. Returning null keeps the rest of the payload usable
    // instead of failing the whole fetch — see the per-field handling in FeaturePayloadDecoder.
    // Never log the blob or the key.
    GB.error(errorMessage = "Crypto: failed to decrypt features", throwable = t)
    null
}

/**
 * The [getFeaturesFromEncryptedFeatures] counterpart for the payload's `encryptedSavedGroups`
 * field, returning the raw [JsonObject] of groups, or null on failure. Same arguments, same
 * fail-soft contract.
 */
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
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    GB.error(errorMessage = "Crypto: failed to decrypt saved groups", throwable = t)
    null
}

/**
 * The [getFeaturesFromEncryptedFeatures] counterpart for `encryptedContextualBandits`, returning
 * the bandit rules keyed by experiment id, or null on failure. Same arguments, same fail-soft
 * contract — a payload whose bandits cannot be decrypted still delivers its features.
 */
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
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    GB.error(errorMessage = "Crypto: failed to decrypt contextual bandits", throwable = t)
    null
}
