package com.gynda.fridaystm.util

import com.gynda.fridaystm.domain.Activity
import com.gynda.fridaystm.domain.activityForGrade
import com.gynda.fridaystm.data.model.toWireValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payload checks for the reference-data seeder. Pure JVM — the Firestore write
 * itself is not exercised (that needs a live project); what matters is that the
 * documents we would write are internally consistent, because a wrong `activity`
 * or a duplicate id silently breaks `resolveGeofenceTarget` at runtime.
 */
class FirestoreSeederTest {

    @Test
    fun `every seeded fence has a unique id equal to its activity wire value`() {
        val ids = SEED_GEOFENCES.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        SEED_GEOFENCES.forEach { fence ->
            assertEquals("doc id must equal the activity it serves", fence.activity, fence.id)
        }
    }

    @Test
    fun `all four activities are covered with a positive radius`() {
        assertEquals(
            setOf(ActivityType.APEL, ActivityType.TALIM, ActivityType.LARKAM, ActivityType.SENAM),
            SEED_GEOFENCES.map { it.activity }.toSet(),
        )
        SEED_GEOFENCES.forEach { fence ->
            assertTrue("${fence.id} radius must be > 0", fence.radiusMeter > 0)
            assertTrue("${fence.id} lat must be set", fence.lat != 0.0)
            assertTrue("${fence.id} lng must be set", fence.lng != 0.0)
        }
    }

    @Test
    fun `every pembiasaan activity is reachable by the rotation`() {
        // resolveGeofenceTarget matches on the wire value produced by the rotation,
        // so each rotating activity must have a fence to resolve to.
        val seeded = SEED_GEOFENCES.map { it.activity }.toSet()
        Activity.entries.forEach { activity ->
            assertTrue("no fence seeded for $activity", activity.toWireValue() in seeded)
        }
    }

    @Test
    fun `fallback rotation maps all three grades and matches the cyclic formula`() {
        assertEquals(setOf("10", "11", "12"), FALLBACK_ROTATION.mapping.keys)
        (10..12).forEach { grade ->
            assertEquals(
                activityForGrade(grade, 0).toWireValue(),
                FALLBACK_ROTATION.mapping[grade.toString()],
            )
        }
        // Bijection: three grades never share an activity in the same week.
        assertEquals(3, FALLBACK_ROTATION.mapping.values.toSet().size)
    }
}
