package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.model.GBValue

/**
 * Opt-in capability for plugins that want explicit [com.sdk.growthbook.GrowthBookSDK.logEvent]
 * calls, on top of the automatic experiment/feature callbacks a [GrowthBookPlugin] already receives.
 *
 * Declared as its own interface rather than another member on [GrowthBookPlugin] because Kotlin
 * interfaces export to Objective-C with every member `@required`: a new member would break existing
 * Swift conformances even with a default body here. A brand-new interface breaks nobody, and a
 * plugin adopts it only when it needs the events. Every future optional hook should follow the same
 * shape.
 *
 * Implementations must be thread-safe and return quickly; exceptions are swallowed by
 * [PluginRegistry] exactly as they are for [GrowthBookPlugin].
 */
interface CustomEventReceiver {

    /**
     * Invoked for each [com.sdk.growthbook.GrowthBookSDK.logEvent] call, never for the SDK's own
     * `Feature Evaluated` / `Experiment Viewed` events — those keep arriving through
     * [GrowthBookPlugin] as typed results. (A consumer who wants all three as one flat stream
     * registers a [com.sdk.growthbook.GBEventLogger] instead of writing a plugin.) [attributes] is
     * the instance's attribute snapshot at call time.
     */
    fun onEvent(
        eventName: String,
        properties: Map<String, GBValue>,
        attributes: Map<String, GBValue>? = null
    )
}
