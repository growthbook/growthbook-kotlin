package com.sdk.growthbook.redis

/**
 * In-memory [GBRedisCommands] that records its calls, so tests can assert both the values
 * exchanged with Redis and the number of round trips.
 *
 * Failures are armed per method: a write that fails must not stop reads from working.
 */
internal class FakeRedisCommands : GBRedisCommands {

    /** The stored keys and values; pre-populate it to stand in for data another instance wrote. */
    val store = mutableMapOf<String, String>()

    /** Keys passed to each [get] call, in order. */
    val getCalls = mutableListOf<String>()

    /** Key lists passed to each [mget] call, in order — one entry per round trip. */
    val mgetCalls = mutableListOf<List<String>>()

    /** Key/value pairs passed to each [set] call, in order — one entry per round trip. */
    val setCalls = mutableListOf<Pair<String, String>>()

    /** Expiry passed to each [set] call, keyed by key; `null` means the write carried no TTL. */
    val ttls = mutableMapOf<String, Long?>()

    /** When set, [get] throws it — after recording the call, so the attempt stays observable. */
    var failGetWith: Throwable? = null

    /** When set, [set] throws it — after recording the call, so the attempt stays observable. */
    var failSetWith: Throwable? = null

    /** When set, [mget] throws it — after recording the call, so the attempt stays observable. */
    var failMgetWith: Throwable? = null

    override suspend fun get(key: String): String? {
        getCalls += key
        failGetWith?.let { throw it }
        return store[key]
    }

    override suspend fun set(key: String, value: String, ttlSeconds: Long?) {
        setCalls += key to value
        failSetWith?.let { throw it }
        store[key] = value
        ttls[key] = ttlSeconds
    }

    override suspend fun mget(keys: List<String>): List<String?> {
        mgetCalls += keys
        failMgetWith?.let { throw it }
        return keys.map { store[it] }
    }
}
