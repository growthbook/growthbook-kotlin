package com.sdk.growthbook.caffeine

import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * A [Ticker] the test drives by hand, so expiry is asserted by advancing time rather than by
 * sleeping — deterministic, and it costs no wall-clock.
 */
internal class FakeTicker : Ticker {

    private val nanos = AtomicLong(0)

    override fun read(): Long = nanos.get()

    fun advance(duration: Duration) {
        nanos.addAndGet(duration.inWholeNanoseconds)
    }
}
