package com.sdk.growthbook.utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.stickybucket.GBStickyBucketService

internal class StickyBucketServiceHelper(private val stickyBucketService: GBStickyBucketService) {
    private val jobs: ArrayList<Job> = ArrayList()

    fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        launchJob {
            stickyBucketService.saveAssignments(doc)
        }
    }

    fun getAllAssignments(
        attributes: Map<String, String>,
        onResult: (Map<String, GBStickyAssignmentsDocument>) -> Unit
    ) {
        launchJob {
            val assignments = stickyBucketService.getAllAssignments(attributes)
            onResult.invoke(assignments)
        }
    }

    private fun launchJob(block: suspend CoroutineScope.() -> Unit) {
        val job = stickyBucketService.coroutineScope.launch(
            block = block,
        )
        jobs.add(job)
    }
}
