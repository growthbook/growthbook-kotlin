package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBFeatureUsageHelper
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBFeatureUsageHelperTests {

    @Test
    fun reportsOncePerValue() {
        val helper = GBFeatureUsageHelper()

        assertTrue(helper.shouldReport("flag", GBString("a")))
        assertFalse(helper.shouldReport("flag", GBString("a")))
        assertTrue(helper.shouldReport("flag", GBString("b")))
        assertFalse(helper.shouldReport("flag", GBString("b")))
    }

    @Test
    fun returningToAPreviousValueReportsAgain() {
        // The helper remembers the last value, not every value ever seen: a flag flipping back is
        // a change like any other.
        val helper = GBFeatureUsageHelper()

        assertTrue(helper.shouldReport("flag", GBString("a")))
        assertTrue(helper.shouldReport("flag", GBString("b")))
        assertTrue(helper.shouldReport("flag", GBString("a")))
    }

    @Test
    fun keysAreIndependent() {
        val helper = GBFeatureUsageHelper()

        assertTrue(helper.shouldReport("flag-a", GBString("v")))
        assertTrue(helper.shouldReport("flag-b", GBString("v")))
        assertFalse(helper.shouldReport("flag-a", GBString("v")))
    }

    @Test
    fun nullAndAbsentAreDifferentStates() {
        // A feature that resolves to no value at all must still report the first time: without the
        // membership check, an unrecorded key and a recorded null would both compare equal to null.
        val helper = GBFeatureUsageHelper()

        assertTrue(helper.shouldReport("missing", null))
        assertFalse(helper.shouldReport("missing", null))
        assertTrue(helper.shouldReport("missing", GBNull))
        assertFalse(helper.shouldReport("missing", GBNull))
    }

    /**
     * How a user change starts a fresh history: a new instance, not a cleared one. Writes to the
     * instance the previous user's evaluations still hold must stay invisible to the replacement.
     */
    @Test
    fun aFreshInstanceReportsEveryKeyAgainAndIsUnaffectedByTheOldOne() {
        val previousUser = GBFeatureUsageHelper()

        assertTrue(previousUser.shouldReport("flag", GBString("a")))
        assertFalse(previousUser.shouldReport("flag", GBString("a")))

        val nextUser = GBFeatureUsageHelper()

        // An evaluation that started before the change reports into the old history...
        assertTrue(previousUser.shouldReport("flag", GBString("b")))
        // ...and does not cost the next user their first report of that same value.
        assertTrue(nextUser.shouldReport("flag", GBString("b")))
        assertFalse(nextUser.shouldReport("flag", GBString("b")))
    }
}
