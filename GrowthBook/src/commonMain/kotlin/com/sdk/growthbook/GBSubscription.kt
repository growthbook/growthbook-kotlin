package com.sdk.growthbook

/**
 * Handle returned by [IGrowthBookSDK.subscribe], used to stop receiving assignment changes.
 *
 * A handle rather than a returned lambda so the API reads the same from Kotlin, Java and Swift —
 * a Kotlin `() -> Unit` exports to Objective-C as an opaque block.
 *
 * [cancel] is idempotent: calling it more than once, or after [GrowthBookSDK.close], is a no-op.
 */
fun interface GBSubscription {
    fun cancel()
}
