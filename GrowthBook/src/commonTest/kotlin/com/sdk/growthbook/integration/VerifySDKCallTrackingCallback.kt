package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilderApp
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Utils.TrackingCallback
import com.sdk.growthbook.local.GrowthBookLocalSDK
import com.sdk.growthbook.model.GBLocalContext
import com.sdk.growthbook.tests.MockNetworkClient
import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VerifySDKCallTrackingCallback {

    @Test
    fun verifyTrackingCallbackNotTriggerNetworkSDK() {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "feature_1": {
                  "defaultValue": true
                }
              }
            }
        """.trimMargin()

        var isTrigger = false

        val trackingCallback: TrackingCallback =
            { _, _ -> isTrigger = true }

        val sdkInstance = buildNetworkSDK(json, trackingCallback)

        sdkInstance.feature("feature_1")

        assertFalse(isTrigger, "Tracking callback not invoke")
    }

    @Test
    fun verifyTrackingCallbackTriggerNetworkSDK() {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "feature_1": {
                  "defaultValue": "value_default",
                  "rules": [
                    {
                      "variations": [
                        "value_1",
                        "value_2"
                      ],
                      "weights": [
                        0.5,
                        0.5
                      ],
                      "hashAttribute": "id"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        var isTrigger = false

        val trackingCallback: TrackingCallback =
            { _, _ -> isTrigger = true }

        val sdkInstance = buildNetworkSDK(json, trackingCallback)

        sdkInstance.feature("feature_1")

        assertTrue(isTrigger, "Tracking callback invoke")
    }

    @Test
    fun verifyTrackingCallbackNotTriggerLocalSDK() {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "feature_1": {
                  "defaultValue": true
                }
              }
            }
        """.trimMargin()

        var isTrigger = false

        val trackingCallback: TrackingCallback =
            { _, _ -> isTrigger = true }

        val sdkInstance = buildLocalSDK(json, trackingCallback)

        sdkInstance.feature("feature_1")

        assertFalse(isTrigger, "Tracking callback not invoke")
    }

    @Test
    fun verifyTrackingCallbackTriggerLocalSDK() {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "feature_1": {
                  "defaultValue": "value_default",
                  "rules": [
                    {
                      "variations": [
                        "value_1",
                        "value_2"
                      ],
                      "weights": [
                        0.5,
                        0.5
                      ],
                      "hashAttribute": "id"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        var isTrigger = false

        val trackingCallback: TrackingCallback =
            { _, _ -> isTrigger = true }

        val sdkInstance = buildLocalSDK(json, trackingCallback)

        sdkInstance.feature("feature_1")

        assertTrue(isTrigger, "Tracking callback invoke")
    }

    private fun buildNetworkSDK(json: String, trackingCallback: GBTrackingCallback): GrowthBookSDK {
        return GBSDKBuilderApp(
            "some_key",
            "http://host.com",
            attributes = mapOf("id" to "some_mock_id"),
            trackingCallback = trackingCallback
        ).setNetworkDispatcher(
            MockNetworkClient(
                json,
                null
            )
        )
            .initialize()
    }

    private fun buildLocalSDK(
        json: String,
        trackingCallback: GBTrackingCallback,
    ): GrowthBookLocalSDK {
        return GrowthBookLocalSDK(
            GBLocalContext(
                true,
                mapOf("id" to "some_mock_id"),
                mapOf(),
                false
            ),
            json,
            trackingCallback,
        )
    }
}
