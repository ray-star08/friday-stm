package com.gynda.fridaystm.util

/**
 * Pure, Android-free model of the runtime location-permission decision, extracted
 * from `HomeScreen` so it is unit-testable on the JVM without Robolectric (task 4.1).
 *
 * The Composable keeps the framework side (the ActivityResult launcher, the Settings
 * intent); these functions own the *decisions* — what a launcher result means, and
 * whether the check-in geofence gate should stay quiet, prompt, pass, or route to
 * Settings. No `android.*` imports here, so a plain JUnit test drives every branch.
 */
data class LocationPermissionState(
    val isGranted: Boolean,
    val isPermanentlyDenied: Boolean,
)

/** What the check-in geofence gate should do given the current permission state. */
sealed interface LocationGateResult {
    /** No geofenced check-in is active — do nothing, don't prompt. */
    data object Idle : LocationGateResult

    /** Location is granted — let the geofence gate proceed. */
    data object Pass : LocationGateResult

    /** Not granted yet but still promptable — show the system permission dialog. */
    data object RequestPermission : LocationGateResult

    /** Permanently denied — the dialog won't show again; route to app Settings. */
    data object NavigateToSettings : LocationGateResult
}

/**
 * Reduce an `ActivityResultContracts.RequestMultiplePermissions` result to a
 * [LocationPermissionState].
 *
 * - Empty map = the dialog was dismissed without a choice, so it is **not** a
 *   permanent denial (no decision was made yet).
 * - Option A (task 4.1): a non-empty result that grants neither permission is
 *   treated as a permanent denial, routing straight to Settings rather than
 *   re-prompting. This is intentional for a discipline app — one prompt, then
 *   Settings — and does not consult `shouldShowRequestPermissionRationale`.
 */
fun reducePermissionResult(result: Map<String, Boolean>): LocationPermissionState {
    if (result.isEmpty()) {
        return LocationPermissionState(isGranted = false, isPermanentlyDenied = false)
    }
    // ponytail: any granted entry counts as "location granted" — safe because the
    // launcher only ever requests FINE + COARSE together. If this is reused for a
    // mixed permission request, match the location keys explicitly instead.
    val granted = result.values.any { it }
    return LocationPermissionState(isGranted = granted, isPermanentlyDenied = !granted)
}

/**
 * Decide what the check-in gate should do. Kept separate from
 * [reducePermissionResult] so the "when do we prompt" policy is testable on its own.
 */
fun resolveLocationGate(
    needsLocation: Boolean,
    state: LocationPermissionState,
): LocationGateResult = when {
    !needsLocation -> LocationGateResult.Idle
    state.isGranted -> LocationGateResult.Pass
    state.isPermanentlyDenied -> LocationGateResult.NavigateToSettings
    else -> LocationGateResult.RequestPermission
}
