package com.sdk.growthbook.integration

// import io.mockk.mockk
// import io.mockk.every
// import com.sdk.growthbook.GrowthBookSDK
// import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
// import com.sdk.growthbook.model.GBFeatureResult
// import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.toGbNumber
import com.sdk.growthbook.utils.GBError
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertEquals
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.features.FetchResult
import com.sdk.growthbook.kotlinx.serialization.gbSerialize

internal class VerifySDKReturnFeatureValues {

    @Test
    fun verifySDKReturnFeatureDefaultValue() = runTest {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "bool_feature_true": {
                  "defaultValue": true
                },
                "bool_feature_false": {
                  "defaultValue": false
                },
                "string_feature": {
                  "defaultValue": "Default value"
                },
                "number_feature": {
                  "defaultValue": 888
                },
                "number_feature_negative": {
                  "defaultValue": -1
                }
              }
            }
        """.trimMargin()

        val sdkInstance = buildSDK(json)

        // maybe we should think about Generic type of value just like in Java SDK

        assertEquals(true, sdkInstance.feature<Boolean>("bool_feature_true"))
        assertEquals(false, sdkInstance.feature<Boolean>("bool_feature_false"))
        assertEquals("Default value", sdkInstance.feature<String>("string_feature"))

        assertEquals(888, sdkInstance.feature<Int>("number_feature"))
        assertEquals(-1, sdkInstance.feature<Int>("number_feature_negative"))
    }

    @Test
    fun verifySDKReturnFeatureValueByConditionIfAttributeDoesNotExist() = runTest {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "string_feature": {
                  "defaultValue": "Default value",
                  "rules": [
                    {
                      "condition": {
                        "country": "IN"
                      },
                      "force": "Default value for country:IN"
                    },
                    {
                      "condition": {
                        "brand": "KZ"
                      },
                      "force": "Default value for brand:KZ"
                    }
                  ]
                }
              }
            }
        """.trimMargin()

        val attributes = mapOf(
            "brand" to GBString("KZ")
        )

        val sdkInstance = buildSDK(json, attributes)
        assertEquals(
            expected = "Default value for brand:KZ",
            actual = sdkInstance.feature<String>("string_feature"),
        )
    }

    @Test
    fun verifySDKAttributesCastingTypes() = runTest {
        @Language("json")
        val json = """
{
      "status": 200,
      "features": {
        "test_feature": {
          "defaultValue": "code",
          "rules": [
            {
              "variations": [
                "override", "control"
              ],
              "coverage": 1,
              "weights": [
                0.5, 0.5
              ],
              "key": "test_feature",
              "hashAttribute": "user_id"
            }
          ]
        }
      }
    }
""".trimMargin()

        //Check casting with Integer value
        val sdkInstance = buildSDK(json = json, attributes = mapOf("user_id" to 123.toGbNumber()))
        val intAttributeValue = sdkInstance.getGBContext().attributes["user_id"]?.gbSerialize()?.toString() ?: ""
        assertEquals("123", intAttributeValue)
        val intFeature = sdkInstance.feature("test_feature")
        assertEquals("experiment", intFeature.source.name)

        //Check casting with Boolean value
        sdkInstance.setAttributes(attributes = mapOf("user_id" to GBBoolean(true)))
        val boolAttributeValue = sdkInstance.getGBContext().attributes["user_id"]?.gbSerialize()?.toString() ?: ""
        assertEquals("true", boolAttributeValue)
        val boolFeature = sdkInstance.feature("test_feature")
        assertEquals("experiment", boolFeature.source.name)

        //Check casting with Float value
        sdkInstance.setAttributes(attributes = mapOf("user_id" to 1.8f.toGbNumber()))
        val floatAttributeValue = sdkInstance.getGBContext().attributes["user_id"]?.gbSerialize()?.toString() ?: ""
        assertEquals("1.8", floatAttributeValue)
        val floatFeature = sdkInstance.feature("test_feature")
        assertEquals("experiment", floatFeature.source.name)

        //Checking with wrong attribute key
        sdkInstance.setAttributes(attributes = mapOf("user_id" to 5.toGbNumber()))
        val wrongKeyAttributeValue =
            sdkInstance.getGBContext().attributes["user_iiii"]?.toString() ?: "wrongIdDefaultValue"
        assertEquals("wrongIdDefaultValue", wrongKeyAttributeValue)
        val wrongKeyFeature = sdkInstance.feature("test_feature")
        assertEquals("experiment", wrongKeyFeature.source.name)
    }

    @Test
    fun `if features fetch fails, suspendFeature() method retries via awaitRefresh()`() = runTest {
        val gbSdk = buildSDK(json = "{}", attributes = emptyMap()) // buildSDK() calls refreshCache(),
        // refreshCache() calls fetchFeature()
        // fetchFeature() triggers featuresFetchFailed()

        val mockedFeaturesViewModel: FeaturesViewModel = mockk {
            coEvery { awaitRefresh() } returns FetchResult.Failed
        }
        gbSdk.featuresViewModel = mockedFeaturesViewModel

        val job = launch {
            gbSdk.suspendFeature("some-feature-id")
        }

            // it is not mandatory here but just to emphasize that
            // if call failed, then suspendFeature() retries via the coalesced awaitRefresh()
            gbSdk.featuresFetchFailed(GBError(null), true)

        delay(100) // cancel only after 100 millis of waiting
        job.cancel()
        // or gbSdk.payloadFetchedSuccessfully(emptyMap(), null, null, true)

        coVerify { mockedFeaturesViewModel.awaitRefresh() }
    }

    @Test
    fun `suspendFeature on Superseded re-joins and returns the fresh value, never the stale one`() = runTest {
        // Companion to FeaturesViewModelTests.testRoundSupersededDuringApplyDoesNotCommitNorReportSuccess:
        // that test proves the final payload-commit fence turns a mid-apply supersession into
        // FetchResult.Superseded (not Success). This test proves suspendFeature() CONSUMES that signal
        // correctly — on Superseded it re-joins the newest generation instead of returning the stale
        // feature that was current while the superseded (older) round was still in flight.
        val gbSdk = buildSDK(json = "{}", attributes = emptyMap())

        // A stale value is currently in context and the in-flight round is in the Failed/retry state.
        gbSdk.getGBContext().features = mapOf("flag" to GBFeature(GBBoolean(false)))
        gbSdk.featuresFetchFailed(GBError(null), true) // remoteSourceFeaturesFetchResult = Failed

        val mockedFeaturesViewModel: FeaturesViewModel = mockk {
            // 1st awaitRefresh(): the round was superseded (attributes changed mid-apply) → suspendFeature
            //    must `continue`, NOT return the stale value.
            // 2nd awaitRefresh(): the newer round lands, applies the FRESH features and reports success.
            coEvery { awaitRefresh() } returns FetchResult.Superseded andThenAnswer {
                gbSdk.getGBContext().features = mapOf("flag" to GBFeature(GBBoolean(true)))
                gbSdk.payloadFetchedSuccessfully(
                    features = gbSdk.getGBContext().features,
                    savedGroups = null,
                    contextualBandits = null,
                    isRemote = true
                )
                FetchResult.Success
            }
        }
        gbSdk.featuresViewModel = mockedFeaturesViewModel

        val result = gbSdk.suspendFeature("flag")

        assertEquals(
            true,
            result.on,
            "suspendFeature must return the fresh value applied after supersession, not the stale one",
        )
        coVerify(exactly = 2) { mockedFeaturesViewModel.awaitRefresh() }
    }

/*
    @Test
    fun `It should be possible to mock feature() method with mockk`() {
        val someFeatureKey = "some-feature-key"
        val expectedFeatureValue = 5
        val mockedResult = GBFeatureResult(
            gbValue = GBNumber(expectedFeatureValue),
            source = GBFeatureSource.defaultValue,
        )
        val gb: GrowthBookSDK = mockk {
            every { feature(someFeatureKey) } returns mockedResult
        }
        val featureValue = gb.feature<Int>(someFeatureKey)
        assertEquals(expectedFeatureValue, featureValue)
    }
*/
}