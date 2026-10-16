package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.model.GBValue

/**
 * Opt-in capability for plugins that keep per-user state and therefore need to know when the
 * instance starts serving a different user — login, logout, account switching.
 *
 * Declared as its own interface rather than another member on [GrowthBookPlugin] for the reason
 * given on [CustomEventReceiver]: Kotlin interfaces export to Objective-C with every member
 * `@required`, so a new member would break existing Swift conformances even with a default body
 * here. A brand-new interface breaks nobody, and a plugin adopts it only when it has state to
 * reset.
 *
 * Implementations must be thread-safe and return quickly — this runs on the caller's thread,
 * inside `setAttributes`. Exceptions are swallowed by [PluginRegistry] exactly as they are for
 * [GrowthBookPlugin].
 */
interface AttributesChangeReceiver {

    /**
     * Invoked after the instance's attributes were replaced or merged and the new map differs from
     * the previous one. Re-setting an identical map does not fire.
     *
     * [attributes] is the new snapshot. It is consumer data: treat it as you would the attributes
     * handed to the event callbacks — never log it.
     */
    fun onAttributesChanged(attributes: Map<String, GBValue>)
}
