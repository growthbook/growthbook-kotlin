package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.tests.MockNetworkClient
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

        assertEquals(true, sdkInstance.feature("bool_feature_true").value)
        assertEquals(false, sdkInstance.feature("bool_feature_false").value)
        assertEquals("Default value", sdkInstance.feature("string_feature").value)

        assertEquals(888, sdkInstance.feature("number_feature").value)
        assertEquals(-1, sdkInstance.feature("number_feature_negative").value)
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
            "brand" to "KZ"
        )

        val sdkInstance = buildSDK(json, attributes)

        assertEquals("Default value for brand:KZ", sdkInstance.feature("string_feature").value)
    }

    private fun buildSDK(json: String, attributes: Map<String, Any> = mapOf()): GrowthBookSDK {
        return GBSDKBuilderApp(
            "some_key",
            "http://host.com",
            attributes = attributes,
            trackingCallback = { _, _ -> }).setNetworkDispatcher(
            MockNetworkClient(
                json,
                null
            )
        ).initialize()
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
                  "hashAttribute": "id"
                }
              ]
            }
          }
        }
    """.trimMargin()

        val sdkInstance = buildSDK(json = json, attributes = mapOf("id" to "123"))

        val attributeValue = sdkInstance.getGBContext().attributes.getOrDefault("id", "").toString()
        assertEquals("123", attributeValue)

        val feature = sdkInstance.feature("test_feature")
        assertEquals("experiment", feature.source.name)
    }
}