package com.sdk.growthbook.local

import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.test.assertTrue

internal class GrowthBookLocalSDKTest {

    @Test
    fun verifyLocalSDKSetCorrectLocalValue() {
        @Language("json")
        val json = """
            {
              "status": 200,
              "features": {
                "test_feature": {
                  "defaultValue": "Default value"
                },
                "new_feature": {
                  "defaultValue": true
                },
                "new_feature_into_new_project": {
                  "defaultValue": true
                }
              }
            }
        """.trimMargin()

        val sdk = GrowthBookLocalSDK(
            mockAttributes(),
            enabled = true,
            forcedVariations = emptyMap(),
            qaMode = false,
            json
        )
        assertEquals(true, sdk.feature("new_feature")?.value)
        assertEquals("Default value", sdk.feature("test_feature")?.value)

    }

    private fun mockAttributes(): Map<String, Any> {
        return mapOf()
    }
}