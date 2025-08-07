package com.sdk.growthbook.utils

import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.platformDependentRunBlocking
import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.stickybucket.GBStickyBucketService

internal class StickyBucketServiceHelper(private val stickyBucketService: GBStickyBucketService) {
    fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        CoroutineScope(PlatformDependentIODispatcher).launch {
            stickyBucketService.saveAssignments(doc)
        }
    }

    fun getAllAssignments(attributes: Map<String, String>): Map<String, GBStickyAssignmentsDocument> {
        val nullableResult = platformDependentRunBlocking {
            stickyBucketService.getAllAssignments(attributes)
        }
        return nullableResult ?: emptyMap()
    }
}
