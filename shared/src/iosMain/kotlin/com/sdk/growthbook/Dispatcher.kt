package com.sdk.growthbook

import kotlin.coroutines.*
import kotlinx.coroutines.*
import platform.darwin.*

// 1
internal actual val ApplicationDispatcher: CoroutineDispatcher =
    NsQueueDispatcher(dispatch_get_main_queue())

// 2
internal class NsQueueDispatcher(
    private val dispatchQueue: dispatch_queue_t
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatchQueue) {
            block.run()
        }
    }
}