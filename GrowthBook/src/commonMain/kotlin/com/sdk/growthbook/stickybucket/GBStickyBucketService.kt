package com.sdk.growthbook.stickybucket

import com.sdk.growthbook.utils.GBStickyAssignmentsDocument

interface GBStickyBucketService {
    fun getAssignments(attributeName: String, attributeValue: String): GBStickyAssignmentsDocument?
    fun saveAssignments(doc: GBStickyAssignmentsDocument)
    fun getAllAssignments(attributes: Map<String, String>): Map<String, GBStickyAssignmentsDocument>
}
