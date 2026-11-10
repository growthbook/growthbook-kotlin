package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.evaluators.GBExperimentHelper
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBExperimentHelperTests {

    private val experiment = GBExperiment(
        key = "exp",
        variations = listOf(GBString("control"), GBString("treatment")),
        hashAttribute = "id",
    )

    private fun resultFor(user: String, variationId: Int = 0) = GBExperimentResult(
        inExperiment = true,
        variationId = variationId,
        value = GBString("control"),
        hashAttribute = "id",
        hashValue = user,
    )

    @Test
    fun tracksAnExposureOnlyOnce() {
        val helper = GBExperimentHelper()

        assertFalse(helper.isTracked(experiment, resultFor("u1")), "first exposure is new")
        assertTrue(helper.isTracked(experiment, resultFor("u1")), "repeat is already tracked")
    }

    @Test
    fun exposuresAreKeyedByUserAndVariation() {
        val helper = GBExperimentHelper()

        assertFalse(helper.isTracked(experiment, resultFor("u1")))
        assertFalse(helper.isTracked(experiment, resultFor("u2")), "another user is a new exposure")
        assertFalse(
            helper.isTracked(experiment, resultFor("u1", variationId = 1)),
            "same user in another variation is a new exposure",
        )
        assertTrue(helper.isTracked(experiment, resultFor("u1")))
    }

    /**
     * The defect this helper's CAS loop exists for: a plain set let every thread pass the
     * membership check before any of them recorded the key, so one exposure produced N tracking
     * callbacks — N rows in the warehouse for a single enrolment.
     */
    @Test
    fun concurrentCallersAreToldTheExposureIsNewExactlyOnce() {
        val threads = 32
        val rounds = 50
        val pool = Executors.newFixedThreadPool(threads)
        try {
            repeat(rounds) {
                val helper = GBExperimentHelper()
                val ready = CountDownLatch(threads)
                val start = CountDownLatch(1)
                val done = CountDownLatch(threads)
                val fresh = AtomicInteger(0)

                repeat(threads) {
                    pool.execute {
                        ready.countDown()
                        start.await()
                        if (!helper.isTracked(experiment, resultFor("u1"))) {
                            fresh.incrementAndGet()
                        }
                        done.countDown()
                    }
                }

                ready.await(10, TimeUnit.SECONDS)
                start.countDown()
                assertTrue(done.await(10, TimeUnit.SECONDS), "workers did not finish in time")
                assertEquals(1, fresh.get(), "exactly one caller may see the exposure as new")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun resetReleasesTheHistory() {
        val helper = GBExperimentHelper()

        assertFalse(helper.isTracked(experiment, resultFor("u1")))
        helper.reset()
        assertFalse(helper.isTracked(experiment, resultFor("u1")), "history was released")
    }

    /**
     * Why the helper survives a user change instead of being swapped like
     * [com.sdk.growthbook.evaluators.GBFeatureUsageHelper]: its key already carries the hash
     * attribute and value, so switching A -> B -> A must not re-announce A's exposure.
     */
    @Test
    fun switchingBackToAPreviousUserDoesNotRefireTheirExposure() {
        val exposures = mutableListOf<String?>()
        val sdk = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://test.com",
            attributes = mapOf("id" to GBString("userA")),
            trackingCallback = { _, result -> exposures.add(result.hashValue) },
            networkDispatcher = MockNetworkClient(null, null),
        ).initialize()

        sdk.run(experiment)
        sdk.setAttributes(mapOf("id" to GBString("userB")))
        sdk.run(experiment)
        sdk.setAttributes(mapOf("id" to GBString("userA")))
        sdk.run(experiment)
        sdk.close()

        assertEquals(listOf<String?>("userA", "userB"), exposures)
    }
}
