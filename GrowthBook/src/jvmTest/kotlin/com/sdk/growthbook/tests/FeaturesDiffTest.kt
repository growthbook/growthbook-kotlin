package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.diffFeatures
import com.sdk.growthbook.utils.GBFeatures
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeaturesDiffTest {

    @Test
    fun `identical features produce no diff`() {
        val features = mapOf("flag1" to GBFeature(defaultValue = GBBoolean(true)))
        val result = diffFeatures(features, features)
        assertFalse { result.hasChanges }
        assertTrue { result.changedKeys.isEmpty() }
    }

    @Test
    fun `add new feature is reported as added`() {
        val old = mapOf("flag1" to GBFeature(defaultValue = GBBoolean(true)))
        val new = old + mapOf("flag2" to GBFeature(defaultValue = GBBoolean(true)))

        val result = diffFeatures(old, new)
        assertEquals(setOf("flag2"), result.added.keys)
        assertTrue { result.removed.isEmpty() && result.changed.isEmpty() }
    }

    @Test
    fun `remove new feature is reported as removed`() {
        val old = mapOf(
            "flag1" to GBFeature(defaultValue = GBBoolean(true)),
            "flag2" to GBFeature(defaultValue = GBBoolean(true))
        )
        val new = mapOf("flag1" to GBFeature(defaultValue = GBBoolean(true)))

        val result = diffFeatures(old, new)
        assertEquals(setOf("flag2"), result.removed.keys)
        assertTrue { result.added.isEmpty() && result.changed.isEmpty() }
    }

    @Test
    fun `change features reports old and new values`() {
        val old = mapOf(
            "flag1" to GBFeature(defaultValue = GBBoolean(true)),
        )
        val new = mapOf("flag1" to GBFeature(defaultValue = GBBoolean(false)))

        val result = diffFeatures(old, new)
        assertEquals(setOf("flag1"), result.changed.keys)
        assertEquals(GBBoolean(true), result.changed["flag1"]?.old?.defaultValue)
        assertEquals(GBBoolean(false), result.changed["flag1"]?.new?.defaultValue)
        assertTrue { result.added.isEmpty() && result.removed.isEmpty() }
    }
}