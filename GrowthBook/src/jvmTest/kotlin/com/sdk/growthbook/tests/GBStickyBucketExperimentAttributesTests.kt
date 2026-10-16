package com.sdk.growthbook.tests

import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Auto-experiments (URL redirects) sit at the payload root rather than inside a feature, so their
 * identifiers have to be derived for sticky bucketing just like feature-rule experiments are —
 * mirrors `deriveStickyBucketIdentifierAttributes` in the reference TS SDK, which walks both
 * `features` and `experiments`.
 */
class GBStickyBucketExperimentAttributesTests {

    private fun context() = GBContext(
        apiKey = "",
        enabled = true,
        attributes = mapOf(
            "id" to GBString("user-1"),
            "deviceId" to GBString("device-1"),
            "anonymousId" to GBString("anon-1"),
        ),
        forcedVariations = emptyMap(),
        qaMode = false,
        trackingCallback = { _, _ -> },
        encryptionKey = "",
        stickyBucketService = GBStickyBucketServiceImp(
            coroutineScope = TestScope(),
            localStorage = MapCachingLayer()
        )
    )

    private fun redirectExperiment() = GBExperiment(
        key = "redirect-exp",
        variations = listOf(GBNumber(0), GBNumber(1)),
        hashAttribute = "deviceId",
        fallBackAttribute = "anonymousId",
    )

    @Test
    fun testDerivesIdentifiersFromPayloadExperiments() {
        val gbContext = context()

        runBlocking {
            GBUtils.refreshStickyBuckets(
                context = gbContext,
                data = FeaturesDataModel(
                    features = emptyMap(),
                    experiments = listOf(redirectExperiment())
                ),
                attributeOverrides = emptyMap()
            )
        }

        val derived = gbContext.stickyBucketIdentifierAttributes.orEmpty()
        assertTrue(derived.contains("deviceId"), "expected hashAttribute, got $derived")
        assertTrue(derived.contains("anonymousId"), "expected fallBackAttribute, got $derived")
    }

    @Test
    fun testDefaultsToIdWhenExperimentHasNoHashAttribute() {
        val gbContext = context()

        runBlocking {
            GBUtils.refreshStickyBuckets(
                context = gbContext,
                data = FeaturesDataModel(
                    features = emptyMap(),
                    experiments = listOf(GBExperiment(key = "exp", variations = emptyList()))
                ),
                attributeOverrides = emptyMap()
            )
        }

        assertEquals(listOf("id"), gbContext.stickyBucketIdentifierAttributes)
    }

    /**
     * No payload in hand (the attribute-change path, `refreshStickyBuckets(data = null)`) — the
     * experiments already published on the context have to be used, as TS falls back to
     * `ctx.global.experiments`.
     */
    @Test
    fun testFallsBackToContextExperimentsWhenNoPayloadGiven() {
        val gbContext = context()
        gbContext.applyPayload(
            features = emptyMap(),
            savedGroups = null,
            contextualBandits = null,
            experiments = listOf(redirectExperiment()),
        )

        runBlocking {
            GBUtils.refreshStickyBuckets(
                context = gbContext,
                data = null,
                attributeOverrides = emptyMap()
            )
        }

        val derived = gbContext.stickyBucketIdentifierAttributes.orEmpty()
        assertTrue(derived.contains("deviceId"), "expected hashAttribute, got $derived")
        assertTrue(derived.contains("anonymousId"), "expected fallBackAttribute, got $derived")
    }
}
