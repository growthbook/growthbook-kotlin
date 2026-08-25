package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.plugin.TrackingPluginConfig
import com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin
import kotlin.test.Test

/**
 * Lives in `commonTest` on purpose: it runs on every target.
 *
 * The plugin's default coroutine scope used to be built from
 * `PlatformDependentIODispatcher.limitedParallelism(1)`, which throws on js and wasmJs — the
 * constructor crashed there before a single event was recorded. The rest of the plugin suite is
 * JVM-only, so nothing caught it. Any regression that makes construction platform-dependent
 * again fails here on the affected target.
 */
class TrackingPluginConstructionTest {

    @Test
    fun pluginIsConstructibleWithTheDefaultScope() {
        val plugin = GrowthBookTrackingPlugin(TrackingPluginConfig(clientKey = "test-key"))

        plugin.init()
        plugin.close()
    }

    @Test
    fun pluginAcceptsEventsWithTheDefaultScope() {
        val plugin = GrowthBookTrackingPlugin(TrackingPluginConfig(clientKey = "test-key"))
        plugin.init()

        // No network dispatcher is configured, so nothing leaves the process; this only has to
        // reach the plugin's scope without the dispatcher rejecting the work.
        plugin.onFeatureEvaluated(
            featureKey = "new-home",
            result = GBFeatureResult(
                gbValue = GBString("Hi"),
                on = true,
                off = false,
                source = GBFeatureSource.defaultValue,
            ),
            attributes = mapOf("user_id" to GBString("u-1")),
        )

        plugin.close()
    }
}
