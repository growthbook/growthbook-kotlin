package com.sdk.growthbook.ext

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBFeatureRefreshEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Feature refresh attempts as a [Flow] — the same events
 * [GrowthBookSDK.addFeatureRefreshListener] delivers, with the subscription tied to the collecting
 * coroutine instead of to a handle you have to remember to cancel.
 *
 * ```kotlin
 * viewModelScope.launch {
 *     sdk.featureRefreshFlow()
 *         .filter { it.success }
 *         .collect { render(it.features) }
 * }
 * ```
 *
 * Cold: each collector registers its own listener on collection and removes it when the collecting
 * coroutine ends — cancelled, completed or failed. Nothing is replayed, so a collector only sees
 * refreshes that happen while it is collecting; read [GrowthBookSDK.getFeatures] for the current
 * state on start.
 *
 * Events are emitted on whichever thread the SDK reports them from — usually its payload-processing
 * dispatcher, but a cache read comes synchronously on the thread calling `initialize()` /
 * `refreshCache()`. The flow does not switch context, so apply your own `flowOn` if a collector
 * must run elsewhere. A slow collector never blocks a refresh regardless: the producer only
 * `trySend`s, and the buffer keeps the latest event and drops what it has overtaken, which is the
 * right trade for state — an event describing definitions that are already superseded is of no use.
 *
 * Collecting an SDK that is already closed completes immediately — a closed instance will never
 * refresh again, so there is nothing to wait for. Closing it *while* a collector is attached does
 * not end the flow, though: the SDK has no termination signal to deliver, so the collector simply
 * stops receiving. Scope the collection (`viewModelScope`, a `SupervisorJob` you cancel) rather
 * than relying on the flow to complete on its own.
 *
 * Kotlin-only ergonomics, and hidden from the Objective-C header: `Flow` has no usable
 * representation there, so iOS consumers use [GrowthBookSDK.addFeatureRefreshListener] directly.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun GrowthBookSDK.featureRefreshFlow(): Flow<GBFeatureRefreshEvent> = callbackFlow {
    val subscription = addFeatureRefreshListener { event ->
        // trySend, not send: listeners run on the SDK's own dispatcher and must never be suspended
        // by a slow collector. Paired with DROP_OLDEST below, it cannot fail on a full buffer.
        trySend(event)
    }
    // Checked after registering, not before: a close() racing this would otherwise leave the
    // collector suspended in awaitClose on a registration that was already refused. Reading the
    // flag second means either the registration won (and a later close is the documented
    // no-termination case) or it was refused and we see that here.
    if (isClosed) {
        subscription.cancel()
        close()
        return@callbackFlow
    }
    awaitClose { subscription.cancel() }
}.buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
