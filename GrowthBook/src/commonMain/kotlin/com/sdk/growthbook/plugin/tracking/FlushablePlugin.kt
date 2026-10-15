package com.sdk.growthbook.plugin.tracking

/**
 * Opt-in capability for plugins that buffer work and can be told to send it without shutting down.
 *
 * Declared as its own interface rather than another member on [GrowthBookPlugin] for the reason
 * given on [CustomEventReceiver]: Kotlin interfaces export to Objective-C with every member
 * `@required`, so a new member would break existing Swift conformances even with a default body
 * here. A brand-new interface breaks nobody, and a plugin adopts it only when it holds a buffer.
 *
 * Implementing it is what puts a plugin in the fan-out of
 * [com.sdk.growthbook.GrowthBookSDK.flush], so a host app can drain every registered plugin from
 * one call instead of keeping a reference to each. [GrowthBookTrackingPlugin.flush] stays directly
 * callable for the case where you do hold the type.
 *
 * The sibling of [GrowthBookPlugin.close], and the reason both exist: `close` ends a plugin for
 * good, while this one leaves it recording. An app heading to the background needs the second.
 */
interface FlushablePlugin {

    /**
     * Sends whatever is buffered now and leaves the plugin running.
     *
     * Suspends until the buffered work has been handed to whatever transport the plugin uses, not
     * until it has been delivered — implementations generally have no delivery signal to wait on.
     * Must be safe to call repeatedly, with nothing buffered, and after
     * [GrowthBookPlugin.close] (a no-op then).
     *
     * Exceptions are swallowed by [PluginRegistry] exactly as they are for [GrowthBookPlugin], so
     * one plugin failing to flush cannot cost the others theirs.
     */
    suspend fun flush()
}
