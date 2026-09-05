package com.gynda.fridaystm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure permission logic extracted from `HomeScreen` (task 4.1).
 * No Robolectric, no test doubles — the functions take plain values.
 */
class PermissionStateTest {

    // Literal Android permission strings (== Manifest.permission.*), so the map keys
    // match what the real launcher delivers without pulling in the framework.
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"

    // --- reducePermissionResult -------------------------------------------

    @Test
    fun `granting FINE location marks state granted and not permanently denied`() {
        val state = reducePermissionResult(mapOf(fine to true, coarse to false))
        assertTrue(state.isGranted)
        assertFalse(state.isPermanentlyDenied)
    }

    @Test
    fun `granting only COARSE location still counts as granted`() {
        val state = reducePermissionResult(mapOf(fine to false, coarse to true))
        assertTrue(state.isGranted)
        assertFalse(state.isPermanentlyDenied)
    }

    @Test
    fun `denying both permissions is treated as permanently denied (Option A)`() {
        val state = reducePermissionResult(mapOf(fine to false, coarse to false))
        assertFalse(state.isGranted)
        assertTrue(state.isPermanentlyDenied)
    }

    @Test
    fun `an empty result (dialog cancelled) is neither granted nor permanently denied`() {
        val state = reducePermissionResult(emptyMap())
        assertFalse(state.isGranted)
        assertFalse(state.isPermanentlyDenied)
    }

    // --- resolveLocationGate ----------------------------------------------

    @Test
    fun `no active check-in yields Idle regardless of permission`() {
        val denied = LocationPermissionState(isGranted = false, isPermanentlyDenied = true)
        assertEquals(LocationGateResult.Idle, resolveLocationGate(needsLocation = false, state = denied))
    }

    @Test
    fun `needs location and granted yields Pass`() {
        val granted = LocationPermissionState(isGranted = true, isPermanentlyDenied = false)
        assertEquals(LocationGateResult.Pass, resolveLocationGate(needsLocation = true, state = granted))
    }

    @Test
    fun `needs location, not granted, still promptable yields RequestPermission`() {
        val fresh = LocationPermissionState(isGranted = false, isPermanentlyDenied = false)
        assertEquals(
            LocationGateResult.RequestPermission,
            resolveLocationGate(needsLocation = true, state = fresh),
        )
    }

    @Test
    fun `needs location and permanently denied yields NavigateToSettings`() {
        val denied = LocationPermissionState(isGranted = false, isPermanentlyDenied = true)
        assertEquals(
            LocationGateResult.NavigateToSettings,
            resolveLocationGate(needsLocation = true, state = denied),
        )
    }
}
