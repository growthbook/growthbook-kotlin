package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.tests.MockNetworkClient
import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

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

    private fun buildSDK(json: String): GrowthBookSDK {
        return GBSDKBuilderApp(
            "some_key",
            "http://host.com",
            attributes = mapOf(),
            trackingCallback = { _, _ -> }).setNetworkDispatcher(
            MockNetworkClient(
                json,
                null
            )
        ).initialize()
    }
}