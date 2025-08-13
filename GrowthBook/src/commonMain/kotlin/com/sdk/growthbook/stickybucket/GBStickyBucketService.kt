package com.sdk.growthbook.stickybucket

import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument

/**
 * Responsible for reading and writing documents which describe sticky bucket assignments.
 */
interface GBStickyBucketService {

    /**
     * Provide coroutine scope for #getAssignments(), #saveAssignments(),
     * getAllAssignments() methods. It can be scope based on IO dispatcher for example.
     */
    val coroutineScope: CoroutineScope

    /**
     * Method for get assignment document for cache by attribute name and attribute value
     */
    suspend fun getAssignments(attributeName: String, attributeValue: String): GBStickyAssignmentsDocument?

    /**
     * Method for saving assignments document to cache
     */
    suspend fun saveAssignments(doc: GBStickyAssignmentsDocument)

    /**
     * The SDK calls getAllAssignments to populate sticky buckets. This in turn will
     * typically loop through individual getAssignments calls. However, some StickyBucketService
     * instances (i.e. Redis) will instead perform a multi-query inside getAllAssignments instead.
     */
    suspend fun getAllAssignments(attributes: Map<String, String>): Map<String, GBStickyAssignmentsDocument>
}
