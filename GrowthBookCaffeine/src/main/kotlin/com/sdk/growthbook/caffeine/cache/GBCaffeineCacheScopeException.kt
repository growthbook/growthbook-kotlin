package com.sdk.growthbook.caffeine.cache

/**
 * Reported through the `onError` callback when a [GBCaffeineCachingLayer] is asked for a key that
 * is not a feature cache.
 *
 * In practice this means the layer has been wired up as the storage behind the *default* sticky
 * bucket service — see [GBCaffeineCachingLayer] for why that combination silently rebuckets users
 * and what to use instead. It is a configuration error rather than a transient failure, so it is
 * given its own type: it will not resolve on its own.
 *
 * The offending key is deliberately left out of the message — a sticky bucket key embeds the
 * attribute value that identifies the user.
 */
class GBCaffeineCacheScopeException internal constructor(
    message: String
) : IllegalStateException(message)
