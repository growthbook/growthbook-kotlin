package com.sdk.growthbook.integration

import io.mockk.mockk
import io.mockk.every
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.toGbNumber
import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertEquals

internal class VerifySDKReturnFeatureValues {

    @Test
    fun verifySDKReturnFeatureDefaultValue() {
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
    fun verifySDKReturnFeatureValueByConditionIfAttributeDoesNotExist() {
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
    fun verifySDKAttributesCastingTypes() {
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
}