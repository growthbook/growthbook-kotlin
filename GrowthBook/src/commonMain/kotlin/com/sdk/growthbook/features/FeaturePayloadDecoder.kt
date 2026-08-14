package com.sdk.growthbook.features

import com.sdk.growthbook.utils.Crypto
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.utils.getSavedGroupFromEncryptedSavedGroup
import kotlinx.serialization.json.JsonObject

/** Resolves a [FeaturesDataModel] into usable features/savedGroups, decrypting when needed. */
internal class FeaturePayloadDecoder(private val encryptionKey: String?) {
    fun decode(m: FeaturesDataModel) = DecodedPayload(
        features = decode(m.features, m.encryptedFeatures, ::getFeaturesFromEncryptedFeatures),
        savedGroups = decode(m.savedGroups, m.encryptedSavedGroups, ::getSavedGroupFromEncryptedSavedGroup)
    )

    private fun <T: Map<*, *>> decode(plain: T?, encrypted: String?, decrypt: (String, String, Crypto?) -> T?) : T? {
        plain?.takeIf { it.isNotEmpty() }?.let { return it } // return if plain features are present
        val key = encryptionKey?.takeIf { it.isNotEmpty() }
        if (encrypted != null && key != null) {
            return decrypt(
                encrypted,
                key,
                DefaultCrypto()
            ) // return decrypted features if plain is absent
        }
        return plain // otherwise return empty features
    }
}

internal data class DecodedPayload(val features: GBFeatures?, val savedGroups: JsonObject?)
