package com.sdk.growthbook.tests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every section of the shared spec (`cases.json`) is either run by a test or excluded here with
 * a reason, so a new section in a spec update can't be skipped without anyone noticing.
 */
class SpecCoverageTests {

    /** Sections the tests run, and the test class that runs each one. */
    private val runSections = mapOf(
        "evalCondition" to "GBConditionTests",
        "hash" to "GBUtilsTests",
        "getBucketRange" to "GBUtilsTests",
        "feature" to "GBFeatureValueTests",
        "run" to "GBExperimentRunTests",
        "chooseVariation" to "GBUtilsTests",
        "inNamespace" to "GBUtilsTests",
        "getEqualWeights" to "GBUtilsTests",
        "decrypt" to "GBEncryptedFeatures",
        "stickyBucket" to "GBStickyBucketingFeatureTests",
        "contextualBandit" to "GBContextualBanditTest",
        "savedGroupReferencesV2.evalCondition" to "GBSavedGroupReferencesV2Tests",
        "savedGroupReferencesV2.feature" to "GBSavedGroupReferencesV2Tests",
        "savedGroupReferencesV2.run" to "GBSavedGroupReferencesV2Tests",
    )

    /** Sections the tests don't run, and why. */
    private val excludedSections = mapOf(
        "getQueryStringOverride" to "This SDK has no querystring/URL experiment overrides",
        "urlRedirect" to "URL redirect experiments are a browser feature",
        "savedGroupReferencesRemote.evalCondition" to "Remote saved groups aren't implemented yet",
        "savedGroupReferencesRemote.feature" to "Remote saved groups aren't implemented yet",
        "pruneCondition" to "The payload reduction methods are not implemented for client SDKs to start with",
        "reducePayload" to "The payload reduction methods are not implemented for client SDKs to start with",
    )

    private val specSections: Set<String> by lazy {
        val usrDir = System.getProperty("user.dir")
        val cases = Json.parseToJsonElement(
            File("$usrDir/src/jvmTest/kotlin/com/sdk/growthbook/tests/cases.json").readText()
        ).jsonObject
        cases.flatMap { (name, value) ->
            when (value) {
                is JsonArray -> listOf(name)
                is JsonObject -> value.keys.map { "$name.$it" }
                else -> emptyList() // specVersion
            }
        }.toSet()
    }

    @Test
    fun everySpecSectionIsRunOrExcluded() {
        val unaccounted = specSections - runSections.keys - excludedSections.keys
        assertEquals(emptySet(), unaccounted, "Run these spec sections or exclude them with a reason")
    }

    @Test
    fun listedSectionsExistInTheSpec() {
        val unknown = (runSections.keys + excludedSections.keys) - specSections
        assertEquals(emptySet(), unknown, "These sections aren't in cases.json")
    }

    @Test
    fun noSectionIsBothRunAndExcluded() {
        assertEquals(emptySet(), runSections.keys intersect excludedSections.keys)
    }
}
