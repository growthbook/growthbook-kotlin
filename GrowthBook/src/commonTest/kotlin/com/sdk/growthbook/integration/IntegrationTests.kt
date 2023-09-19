package com.sdk.growthbook.integration

import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertEquals

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
}