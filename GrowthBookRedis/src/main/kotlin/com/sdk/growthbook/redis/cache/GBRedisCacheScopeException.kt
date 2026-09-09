package com.sdk.growthbook.redis.cache

/**
 * Reported through the `onError` callback when a [GBRedisCachingLayer] is asked for a key outside
 * the feature cache it was built for.
 *
 * In practice this means the layer has been wired up as the storage behind the *default* sticky
 * bucket service — see [GBRedisCachingLayer] for why that combination cannot work and what to use
 * instead. It is a configuration error rather than a Redis outage, so it is given its own type:
 * unlike a transient failure it will not resolve on its own.
 *
 * The offending key is deliberately left out of the message — a sticky bucket key embeds the
 * attribute value that identifies the user.
 */
class GBRedisCacheScopeException internal constructor(
    message: String
) : IllegalStateException(message)
