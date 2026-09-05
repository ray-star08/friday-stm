# Walkthrough - Startup Stability & Fix SecurityException

I have implemented the "Startup Stability & Fix SecurityException" plan to resolve the GMS `SecurityException` and optimize the app's startup performance.

## Changes Made

### 1. Build Stabilization & Dependency Downgrades
- **Downgraded SDK & AGP**: Moved from experimental versions (`compileSdk 37`, `AGP 9.3.1`) to stable ones (`compileSdk 35`, `AGP 8.8.1`) to ensure compatibility with Google Play Services.
- **Library Adjustments**: Downgraded `androidx.core`, `androidx.activity`, and `androidx.lifecycle` to versions compatible with API 35.
- **GMS Stability**: Downgraded `play-services-location` to `21.0.1` to avoid known `SecurityException` issues in certain environments.
- **Environment Fix**: Enabled `android.useAndroidX` and `android.enableJetifier` in `gradle.properties`.

### 2. Package Visibility
- **Manifest Queries**: Added a `<queries>` block to [AndroidManifest.xml](file:///E:/AndroidStudioProjects/FridaySTM/app/src/main/AndroidManifest.xml) for `com.google.android.gms`. This ensures that the app has visibility into Google Play Services on Android 11+ devices, satisfying IPC security requirements.

### 3. Navigation & ViewModel Scoping
- **Optimized Scoping**: Refined [AppNavHost.kt](file:///E:/AndroidStudioProjects/FridaySTM/app/src/main/java/com/gynda/fridaystm/ui/navigation/AppNavHost.kt) to ensure `HomeViewModel` is scoped locally to the Home route.
- **Smart Sharing**: Used `remember(backStackEntry)` to share the `HomeViewModel` instance with the Camera route efficiently, preventing redundant lookups and potential memory leaks.

### 4. UI & Map Performance
- **Asynchronous Config**: Moved the `osmdroid` configuration loading in [GeofenceMiniMap.kt](file:///E:/AndroidStudioProjects/FridaySTM/app/src/main/java/com/gynda/fridaystm/ui/component/GeofenceMiniMap.kt) from a blocking `remember` block to a `LaunchedEffect(Unit)` running on `Dispatchers.IO`. This prevents IO-driven frame skips during the initial render.
- **Intelligent Updates**: Optimized the `AndroidView.update` block to only call `invalidate()` when overlays or positions have actually changed, reducing redundant draws.

## Verification Results

### Automated Tests
- Ran `./gradlew app:assembleDebug` - **SUCCESS**. The project now builds cleanly with stable tools.

### Manual Verification (Recommended)
- Deploy the app to your device.
- Verify that the `SecurityException` no longer appears in Logcat.
- Check that the `Home` screen loads smoothly without stuttering when the map initializes.
