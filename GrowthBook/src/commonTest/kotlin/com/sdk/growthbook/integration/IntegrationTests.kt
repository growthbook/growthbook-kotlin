package com.sdk.growthbook.integration

import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationTests {

    @Test
    fun verifyIsInTheListRule() {
        // User attributes for targeting and assigning users to experiment variations
        val attrs = HashMap<String, Any>()
        attrs["appBuildNumber"] = 3432

        @Language("json")
        val json = """
{
  "status": 200,
  "features": {
    "user576-feature": {
      "defaultValue": false,
      "rules": [
        {
          "condition": {
            "appBuildNumber": {
              "${'$'}in": [
                3432,
                3431
              ]
            }
          },
          "force": true
        }
      ]
    }
  }
}
        """.trimMargin()

        val sdkInstance = buildSDK(json, attrs)

        assertEquals(true, sdkInstance.feature("user576-feature").value)
    }

    @Test
    fun `gBExperimentResult name should not be null`() {
        @Language("json")
        val json = """
{
  "status": 200,
  "features": {
    "post-appointment-all-video-appointments-button": {
      "defaultValue": false,
      "rules": [{
        "coverage": 1,
        "hashAttribute": "id",
        "bucketVersion": 1,
        "seed": "56bc09c2-53b7-457e-9980-c531c4431c59",
        "hashVersion": 2,
        "variations": [false, true],
        "weights": [0, 1],
        "key": "aoc-post-appointment-button-type",
        "meta": [{
          "key": "0",
          "name": "Control"
        }, {
          "key": "1",
          "name": "Button filled"
        }],
        "phase": "1",
        "name": "AoC post appointment button type"
      }]
    }
  },
  "dateUpdated": "2024-03-11T16:40:55.214Z"
}
        """.trimMargin()

        val growthBookSdk = buildSDK(
            json = json,
            attributes = mapOf("id" to "someId"),
            trackingCallback = { _, gbExperimentResult ->
                val variationName = gbExperimentResult.name
                assertTrue((variationName == "Button filled") || (variationName == "Control"))
            }
        )

        growthBookSdk.feature("post-appointment-all-video-appointments-button")
    }
}
