package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBBanditContext
import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [GrowthBookSDK.subscribe] / [GrowthBookSDK.getAllResults].
 *
 * Variations are steered with `setForcedVariations` rather than attributes, so no test depends on
 * the hashing outcome for a particular id: forced assignment is resolved before bucketing, which
 * keeps the expected variation explicit in the test itself.
 */
class GBSubscriptionTests {

    private val experimentKey = "sub-exp"
    private val featureKey = "sub-flag"

    /** A feature whose only rule is an experiment, so evaluating it exercises the feature path. */
    private val featureWithExperimentRule = """
        {"$featureKey":{"defaultValue":"control","rules":[
            {"key":"$experimentKey","variations":["control","treatment"],"weights":[0.5,0.5],
             "hashAttribute":"id"}
        ]}}
    """.trimIndent()

    private fun inlineExperiment() = GBExperiment(
        key = experimentKey,
        variations = listOf(GBString("control"), GBString("treatment")),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun builder(scheduler: TestCoroutineScheduler) =
        GBSDKBuilder(
            apiKey = "key",
            apiHost = "https://example.com",
            attributes = HashMap<String, GBValue>(),
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(null, null),
            cachingEnabled = false,
        )
            .setInitialFeatures(encryptToFeaturesDataModel(featureWithExperimentRule)!!)
            .setCoroutineContext(UnconfinedTestDispatcher(scheduler))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun runFiresOnFirstEvaluation() = runTest {
        val sdk = builder(testScheduler).initialize()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        sdk.run(inlineExperiment())

        assertEquals(1, seen.size)
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun repeatedEvaluationWithSameVariationIsSilent() = runTest {
        val sdk = builder(testScheduler).initialize()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        sdk.run(inlineExperiment())
        sdk.run(inlineExperiment())
        sdk.run(inlineExperiment())

        assertEquals(1, seen.size, "an unchanged assignment must be reported once, not per call")
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun changedVariationFiresAgain() = runTest {
        val sdk = builder(testScheduler).initialize()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        sdk.run(inlineExperiment())
        sdk.setForcedVariations(mapOf(experimentKey to 1))
        sdk.run(inlineExperiment())

        assertEquals(2, seen.size)
        assertEquals(1, seen.last().variationId)
        assertTrue(seen.last().inExperiment)
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun leavingTheExperimentFires() = runTest {
        // An out-of-range forced index falls back to the baseline with inExperiment = false, which is
        // the transition a consumer needs in order to roll a variation back.
        val sdk = builder(testScheduler).initialize()
        sdk.setForcedVariations(mapOf(experimentKey to 1))
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        sdk.run(inlineExperiment())
        assertTrue(seen.single().inExperiment)

        sdk.setForcedVariations(mapOf(experimentKey to 99))
        sdk.run(inlineExperiment())

        assertEquals(2, seen.size, "dropping out of the experiment must be reported")
        assertFalse(seen.last().inExperiment)
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun experimentReachedThroughFeatureRuleFires() = runTest {
        // The main path in an app: the experiment arrives as a feature rule, never as run().
        val sdk = builder(testScheduler).initialize()
        runCurrent()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        sdk.feature(featureKey)

        assertEquals(1, seen.size, "a feature-rule experiment must report its assignment")
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelStopsDeliveryAndIsIdempotent() = runTest {
        val sdk = builder(testScheduler).initialize()
        val seen = mutableListOf<GBExperimentResult>()
        val subscription = sdk.subscribe { _, result -> seen.add(result) }

        sdk.run(inlineExperiment())
        assertEquals(1, seen.size)

        subscription.cancel()
        subscription.cancel()

        sdk.setForcedVariations(mapOf(experimentKey to 1))
        sdk.run(inlineExperiment())

        assertEquals(1, seen.size, "a cancelled subscription must receive nothing")
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theSameCallbackSubscribedTwiceIsTwoSubscriptions() = runTest {
        // Identity-keyed registration (the reference JS SDK uses a Set) would collapse these two and
        // let one caller's cancel() silently kill the other's subscription.
        val sdk = builder(testScheduler).initialize()
        val count = AtomicInteger()
        val callback: (GBExperiment, GBExperimentResult) -> Unit = { _, _ -> count.incrementAndGet() }

        val first = sdk.subscribe(callback)
        sdk.subscribe(callback)

        sdk.run(inlineExperiment())
        assertEquals(2, count.get())

        first.cancel()
        sdk.setForcedVariations(mapOf(experimentKey to 1))
        sdk.run(inlineExperiment())

        assertEquals(3, count.get(), "cancelling one registration must leave the other intact")
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun throwingSubscriberIsIsolated() = runTest {
        val sdk = builder(testScheduler).initialize()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, _ -> throw RuntimeException("boom") }
        sdk.subscribe { _, result -> seen.add(result) }

        val result = sdk.run(inlineExperiment())

        assertEquals(1, seen.size, "a throwing subscriber must not suppress the others")
        assertEquals(0, result.variationId, "evaluation must survive a throwing subscriber")
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun featureFlowDoesNotReportAssignments() = runTest {
        // Reactive re-evaluations are silent by design. If they reported, collecting a flow would
        // announce variations for screens the user never saw AND record them in `assigned`, so the
        // real evaluation that follows would be deduped away and never reach the subscriber.
        val sdk = builder(testScheduler).initialize()
        runCurrent()
        val seen = mutableListOf<GBExperimentResult>()
        sdk.subscribe { _, result -> seen.add(result) }

        val job = backgroundScope.launch { sdk.featureFlow(featureKey).collect { } }
        runCurrent()
        sdk.setForcedVariations(mapOf(experimentKey to 1))
        runCurrent()

        assertTrue(seen.isEmpty(), "a silent evaluation must not report an assignment")
        assertTrue(sdk.getAllResults().isEmpty(), "a silent evaluation must not record an assignment")

        // The first real evaluation still reports, i.e. `assigned` was left clean.
        sdk.feature(featureKey)
        assertEquals(1, seen.size)

        job.cancel()
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getAllResultsExposesLatestAssignmentAsSnapshot() = runTest {
        val sdk = builder(testScheduler).initialize()

        assertTrue(sdk.getAllResults().isEmpty(), "nothing evaluated yet")

        sdk.run(inlineExperiment())
        val snapshot = sdk.getAllResults()
        assertEquals(0, snapshot.getValue(experimentKey).second.variationId)

        sdk.setForcedVariations(mapOf(experimentKey to 1))
        sdk.run(inlineExperiment())

        assertEquals(1, sdk.getAllResults().getValue(experimentKey).second.variationId)
        assertEquals(
            0,
            snapshot.getValue(experimentKey).second.variationId,
            "a previously returned map must not change under the caller"
        )
        sdk.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeReleasesSubscriptionsAndAssignments() = runTest {
        val sdk = builder(testScheduler).initialize()
        sdk.subscribe { _, _ -> }
        sdk.run(inlineExperiment())
        assertFalse(sdk.getAllResults().isEmpty())

        sdk.close()

        assertTrue(sdk.getAllResults().isEmpty())
    }

    /**
     * Builds a bandit-driven feature whose enrollment is decided by [coverage], evaluates it through
     * the feature evaluator with the assignment hook attached, and returns the experiment the hook
     * was handed.
     *
     * Driven at the evaluator level rather than through the SDK because what is under test is the
     * state of the `GBExperiment` object at the moment the hook sees it — `GBExperiment.contextualBandit`
     * is internal and never reaches a consumer through the public surface.
     */
    private fun experimentSeenByHook(coverage: Float): GBExperiment {
        val attributes = mapOf("id" to GBString("u1"))
        val bandit = GBContextualBandit(
            banditVersion = 7,
            contexts = listOf(
                // No condition: a catch-all leaf, so routing never depends on the attributes.
                GBBanditContext(leafId = 3, condition = null, weights = listOf(0.5f, 0.5f))
            )
        )
        val feature = GBFeature(
            rules = listOf(
                GBFeatureRule(
                    key = experimentKey,
                    contextualBanditRef = "bandit_1",
                    contextualVariations = listOf(GBString("control"), GBString("treatment")),
                    coverage = coverage,
                    hashAttribute = "id",
                )
            )
        )

        var captured: GBExperiment? = null
        val context = GBTestHelper.createTestScopeEvaluationContext(
            features = mapOf(featureKey to feature),
            attributes = attributes,
            contextualBandits = mapOf("bandit_1" to bandit),
        ).copy(onExperimentEval = { experiment, _ -> captured = experiment })

        GBFeatureEvaluator(context).evaluateFeature(featureKey, emptyMap())
        return assertNotNull(captured, "the hook must fire for a bandit-driven experiment rule")
    }

    @Test
    fun banditMetadataReachesTheHookForAnEnrolledUser() {
        val experiment = experimentSeenByHook(coverage = 1f)

        val bandit = assertNotNull(experiment.contextualBandit)
        assertEquals(3, bandit.leafId)
        assertEquals(7, bandit.banditVersion)
    }

    @Test
    fun banditMetadataIsStrippedForANonEnrolledUser() {
        // The result already gates the metadata on enrolment, but the experiment object is the one
        // buildContextualBanditExperiment wrote it onto — it has to be stripped there too before the
        // hook can observe it, as the reference SDK does.
        val experiment = experimentSeenByHook(coverage = 0f)

        assertNull(
            experiment.contextualBandit,
            "a user who is not in the experiment must not be handed bandit metadata"
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun concurrentEvaluationsReportTheAssignmentOnce() = runTest {
        // Load-then-store on `assigned` would let several threads each read a null previous entry and
        // all report the same first assignment.
        val sdk = builder(testScheduler).initialize()
        val count = AtomicInteger()
        sdk.subscribe { _, _ -> count.incrementAndGet() }

        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.execute {
                start.await()
                sdk.run(inlineExperiment())
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "workers did not finish")
        pool.shutdown()

        assertEquals(1, count.get(), "one assignment must be reported once, whatever the thread count")
        sdk.close()
    }
}
