package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the configuration DSL against drifting behind [GBSDKBuilder].
 *
 * This module is versioned and released separately from the core SDK, so a setter added to the
 * builder does not break anything here — the DSL simply stops offering it, silently, until someone
 * notices. That is exactly how five settings went missing between releases. This test turns the
 * next such gap into a CI failure at the moment the setter lands.
 *
 * It checks the *setter surface*, not behaviour: that every public `set*` on the builder is either
 * mapped by [GrowthBookConfigBuilder] or listed as a deliberate omission. `ConfigDslTest` covers
 * what the mapped ones actually do.
 *
 * JVM-only because it needs reflection; the DSL itself is common code, and the builder surface it
 * mirrors is identical on every target.
 */
class ConfigDslCoverageTest {

    /** Builder setter -> the [GrowthBookConfigBuilder] property that feeds it. */
    private val coveredByDsl = mapOf(
        "setForcedVariations" to "forceVariations",
        "setQAMode" to "qaMode",
        "setEnabled" to "enabled",
        "setRefreshHandler" to "refreshHandler",
        "setFeaturesChangeHandler" to "featuresChangeHandler",
        "setFeatureUsageCallback" to "featureUsageCallback",
        "setEventLogger" to "eventLogger",
        "setFetchStatsHandler" to "fetchStatsHandler",
        "setInitialFeatures" to "initialFeatures",
        "setInitialPayload" to "initialPayload",
        "setCacheMaxAge" to "cacheMaxAge",
        "setStaleTtl" to "staleTtl",
        "setServeStaleOnError" to "serveStaleOnError",
        "setRefreshInterval" to "refreshInterval",
        "setCachingLayer" to "cachingLayer",
        "setPlugins" to "plugins",
        "setStickyBucketService" to "stickyBucketService / stickyBucketScope",
        "setPrefixForStickyBucketCachedDirectory" to "stickyBucketPrefix",
    )

    /** Builder setters intentionally left out of the DSL, with the reason. */
    private val deliberatelyOmitted = mapOf(
        // `internal` in the core module, so it is mangled out of the reflected surface anyway —
        // kept here so the decision survives if that ever changes.
        "setCoroutineContext" to "internal test seam for injecting a deterministic dispatcher, " +
            "not a consumer-facing option",
    )

    /**
     * Public `set*` methods on the builder, including those inherited from `SDKBuilder`.
     *
     * Names containing `$` are dropped: Kotlin mangles `internal` functions that way, and an
     * internal setter is by definition not part of the surface the DSL mirrors.
     */
    private fun builderSetters(): Set<String> =
        GBSDKBuilder::class.java.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .filter { it.startsWith("set") && '$' !in it }
            .toSet()

    /**
     * Both guards below compare against [builderSetters], so an empty reflected surface would make
     * them pass vacuously — exactly when they are most needed. This pins the surface down instead.
     */
    @Test
    fun `the reflected builder surface is not empty`() {
        // Deliberately not an equality check against the coverage map: that would subsume the two
        // guards below, hiding their diagnostics, and would turn any use of deliberatelyOmitted
        // into a failure here — while instructing the maintainer to add exactly such an entry.
        assertTrue(
            "setCacheMaxAge" in builderSetters(),
            "Reflection over GBSDKBuilder returned no recognisable setters " +
                "(${builderSetters().size} found), which would make the other guards in this " +
                "class pass vacuously.",
        )
    }

    @Test
    fun `every builder setter is mapped by the config DSL`() {
        val missing = builderSetters() - coveredByDsl.keys - deliberatelyOmitted.keys

        assertEquals(
            emptySet(),
            missing,
            "GBSDKBuilder gained setters the DSL does not expose: $missing. " +
                "Add a matching property to GrowthBookConfigBuilder and wire it in build(), " +
                "then list it in coveredByDsl — or, if it is intentionally not a DSL option, " +
                "record it in deliberatelyOmitted with the reason.",
        )
    }

    @Test
    fun `the coverage map has no entries for setters that no longer exist`() {
        val stale = coveredByDsl.keys - builderSetters()

        assertEquals(
            emptySet(),
            stale,
            "These setters are listed as covered but no longer exist on GBSDKBuilder: $stale. " +
                "A renamed or removed setter leaves the DSL wiring dangling — update both.",
        )
    }
}
