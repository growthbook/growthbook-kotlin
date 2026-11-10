package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.utils.Crypto
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.getBanditsFromEncryptedBandits
import com.sdk.growthbook.utils.getExperimentsFromEncryptedExperiments
import com.sdk.growthbook.utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.utils.getSavedGroupFromEncryptedSavedGroup
import kotlinx.serialization.json.JsonObject

/** Resolves a [FeaturesDataModel] into usable features/savedGroups, decrypting when needed. */
internal class FeaturePayloadDecoder(private val encryptionKey: String?) {
    fun decode(m: FeaturesDataModel) = DecodedPayload(
        features = decode(m.features, m.encryptedFeatures, ::getFeaturesFromEncryptedFeatures),
        savedGroups = decode(m.savedGroups, m.encryptedSavedGroups, ::getSavedGroupFromEncryptedSavedGroup),
        contextualBandits = decode(m.contextualBandits, m.encryptedContextualBandits, ::getBanditsFromEncryptedBandits),
        experiments = decodeList(m.experiments, m.encryptedExperiments, ::getExperimentsFromEncryptedExperiments)
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

    /** Same precedence as [decode], for the payload fields that are lists rather than maps. */
    private fun <T> decodeList(
        plain: List<T>?,
        encrypted: String?,
        decrypt: (String, String, Crypto?) -> List<T>?,
    ): List<T>? {
        plain?.takeIf { it.isNotEmpty() }?.let { return it }
        val key = encryptionKey?.takeIf { it.isNotEmpty() }
        if (encrypted != null && key != null) {
            return decrypt(encrypted, key, DefaultCrypto())
        }
        return plain
    }
}

internal data class DecodedPayload(
    val features: GBFeatures?,
    val savedGroups: JsonObject?,
    val contextualBandits: Map<String, GBContextualBandit>? = null,
    val experiments: List<GBExperiment>? = null
)
