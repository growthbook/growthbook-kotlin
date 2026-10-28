package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.sandbox.CachingJvm
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeCallbacksTests {

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private val payload = """{"features":{"flag":{"defaultValue":true}}}"""

    private fun buildSDK(onTrack: (String) -> Unit = {}): GrowthBookSDK = GBSDKBuilder(
        apiKey = "",
        apiHost = "",
        networkDispatcher = MockNetworkClient(successResponse = null, error = null),
        attributes = mapOf("id" to GBString("user-1")),
        trackingCallback = { experiment, _ -> onTrack(experiment.key) },
    ).setInitialPayload(payload).initialize()

    private fun experiment(key: String) =
        GBExperiment(key = key, variations = listOf(GBNumber(0), GBNumber(1)))

    @Test
    fun `new tracking callback receives the next exposure and the old one does not`() {
        val original = mutableListOf<String>()
        val replacement = mutableListOf<String>()
        val sdk = buildSDK { original.add(it) }

        sdk.run(experiment("exp-1"))
        sdk.setTrackingCallback { exp, _ -> replacement.add(exp.key) }
        sdk.run(experiment("exp-2"))

        assertEquals(listOf("exp-1"), original)
        assertEquals(listOf("exp-2"), replacement)
    }

    @Test
    fun `an exposure already tracked is not repeated to the new callback`() {
        val replacement = mutableListOf<String>()
        val sdk = buildSDK()

        sdk.run(experiment("exp-1"))
        sdk.setTrackingCallback { exp, _ -> replacement.add(exp.key) }
        sdk.run(experiment("exp-1"))

        assertTrue(replacement.isEmpty())
    }

    @Test
    fun `a throwing replacement callback does not break evaluation`() {
        val sdk = buildSDK()
        sdk.setTrackingCallback { _, _ -> throw IllegalStateException("sink down") }

        val result = sdk.run(experiment("exp-1"))

        assertTrue(result.inExperiment)
    }

    @Test
    fun `feature usage callback can be replaced and removed`() {
        val usage = mutableListOf<String>()
        val sdk = buildSDK()

        sdk.setFeatureUsageCallback { key, _ -> usage.add(key) }
        sdk.feature("flag")
        assertEquals(listOf("flag"), usage)

        sdk.setFeatureUsageCallback(null)
        sdk.setAttributes(mapOf("id" to GBString("user-2"))) // fresh usage history
        sdk.feature("flag")
        assertEquals(listOf("flag"), usage)
    }

    @Test
    fun `a throwing feature usage callback does not break evaluation`() {
        val sdk = buildSDK()
        sdk.setFeatureUsageCallback { _, _ -> throw IllegalStateException("sink down") }

        assertTrue(sdk.feature("flag").on)
    }
}
