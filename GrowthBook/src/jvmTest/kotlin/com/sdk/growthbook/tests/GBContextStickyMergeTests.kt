package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [GBContext.mergeStickyAssignmentDoc] — the per-key merge that replaced the old
 * whole-map write-back. The key contract is that merging one assignment never drops the other keys
 * already in the context, so it composes with a concurrent background refresh.
 */
class GBContextStickyMergeTests {

    private fun context() = GBContext(
        apiKey = "",
        enabled = true,
        attributes = emptyMap(),
        forcedVariations = emptyMap(),
        qaMode = false,
        trackingCallback = { _, _ -> },
        encryptionKey = "",
    )

    private fun doc(attr: String, value: String) = GBStickyAssignmentsDocument(
        attributeName = attr,
        attributeValue = value,
        assignments = mapOf("exp__0" to value),
    )

    @Test
    fun mergeIntoNullDocsCreatesMap() {
        val gb = context()
        assertNull(gb.stickyBucketAssignmentDocs)

        val d = doc("id", "1")
        gb.mergeStickyAssignmentDoc("id||1", d)

        assertEquals(mapOf("id||1" to d), gb.stickyBucketAssignmentDocs)
    }

    @Test
    fun mergePreservesOtherKeys() {
        val gb = context()
        val existing = doc("id", "1")
        gb.stickyBucketAssignmentDocs = mapOf("id||1" to existing)

        val added = doc("companyId", "42")
        gb.mergeStickyAssignmentDoc("companyId||42", added)

        // Both keys must be present — a whole-map replace would have dropped "id||1".
        assertEquals(
            mapOf("id||1" to existing, "companyId||42" to added),
            gb.stickyBucketAssignmentDocs,
        )
    }

    @Test
    fun mergeReplacesSameKey() {
        val gb = context()
        gb.stickyBucketAssignmentDocs = mapOf("id||1" to doc("id", "1"))

        val updated = doc("id", "1").copy(assignments = mapOf("exp__0" to "treatment"))
        gb.mergeStickyAssignmentDoc("id||1", updated)

        assertEquals(mapOf("id||1" to updated), gb.stickyBucketAssignmentDocs)
    }
}
