package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBFeatures
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same `$nin` exclusion driven through a real feature rule rather than the condition evaluator
 * alone.
 *
 * [MembershipOperatorTests] pins the operator; this pins the symptom that made it worth fixing.
 * A rule whose condition evaluates false is skipped, so the feature silently falls through to its
 * default — "serve everyone except RU/CN" turns into "serve nobody" with no error anywhere. Going
 * through [GBSDKBuilder] also covers the conversion the evaluator tests bypass: rule conditions are
 * parsed into `GBFeatureRule.conditionGB` once at load, so a regression could live in the
 * conversion rather than in the operator.
 */
class MembershipOperatorFeatureRuleTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun testExclusionRuleServesAnUnlistedCountry() {
        assertEquals(ON, exclusionValue(mapOf("country" to GBString("US"))))
    }

    @Test
    fun testExclusionRuleDoesNotServeAListedCountry() {
        assertEquals(OFF, exclusionValue(mapOf("country" to GBString("RU"))))
    }

    @Test
    fun testExclusionRuleServesAUserWhoseAttributeIsNull() {
        assertEquals(
            ON,
            exclusionValue(mapOf("country" to GBNull)),
            "A null country is not RU or CN, so the exclusion rule must still apply",
        )
    }

    @Test
    fun testExclusionRuleServesAUserWithoutTheAttribute() {
        assertEquals(
            ON,
            exclusionValue(emptyMap()),
            "An attribute the app has not populated yet must not silently drop the user from the rule",
        )
    }

    /**
     * The failure mode is indistinguishable from an intentional default unless the source is
     * checked: an unset attribute must still report the rule as the origin of the value, not
     * `defaultValue`.
     */
    @Test
    fun testExclusionRuleIsReportedAsTheSourceForAnAbsentAttribute() {
        val result = evaluate(COUNTRY_EXCLUSION, emptyMap())

        assertEquals(GBFeatureSource.force, result.source)
        assertEquals("exclusion-rule", result.ruleId)
    }

    @Test
    fun testDefaultValueIsReportedForAListedCountry() {
        val result = evaluate(COUNTRY_EXCLUSION, mapOf("country" to GBString("RU")))

        assertEquals(GBFeatureSource.defaultValue, result.source)
    }

    /**
     * The shape reported in growthbook-swift#185: an exclusion on an attribute that only exists
     * after login, ANDed with one that always exists. Logged out, the rule must still serve.
     */
    @Test
    fun testExclusionRuleServesALoggedOutUserWhoseGatedAttributeIsAbsent() {
        @Language("json")
        val condition = """
            {"plan": {"${'$'}nin": ["trial"]}, "country": {"${'$'}nin": ["RU", "CN"]}}
        """

        assertEquals(
            ON,
            (evaluate(condition, mapOf("country" to GBString("US"))).gbValue as? GBString)?.value
        )
        assertEquals(
            OFF,
            (evaluate(condition, mapOf("country" to GBString("RU"))).gbValue as? GBString)?.value
        )
        assertEquals(
            OFF,
            (evaluate(
                condition,
                mapOf("plan" to GBString("trial"), "country" to GBString("US")),
            ).gbValue as? GBString)?.value,
        )
    }

    private fun evaluate(
        @Language("json") condition: String,
        attributes: Map<String, GBValue>,
    ): GBFeatureResult {
        val features: GBFeatures = mapOf(
            FEATURE_KEY to GBFeature(
                defaultValue = GBString(OFF),
                rules = listOf(
                    GBFeatureRule(
                        id = "exclusion-rule",
                        condition = Json.parseToJsonElement(condition),
                        force = GBString(ON),
                    )
                )
            )
        )

        return GBSDKBuilder(
            apiKey = "membership-key",
            apiHost = "https://host.com",
            attributes = attributes,
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(null, null),
            remoteEval = false,
        )
            .setInitialFeatures(features)
            .initialize()
            .feature(FEATURE_KEY)
    }

    private fun exclusionValue(attributes: Map<String, GBValue>): String? =
        (evaluate(COUNTRY_EXCLUSION, attributes).gbValue as? GBString)?.value

    private companion object {
        const val FEATURE_KEY = "my_flag"
        const val ON = "ON"
        const val OFF = "OFF"

        @Language("json")
        const val COUNTRY_EXCLUSION = """{"country": {"${'$'}nin": ["RU", "CN"]}}"""
    }
}
