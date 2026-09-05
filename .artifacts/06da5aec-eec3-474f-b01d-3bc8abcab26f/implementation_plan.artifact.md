# Implementation Plan - Fix SecurityException: Unknown calling package name 'com.google.android.gms'

The user is encountering a `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'` error when running the application. This error typically occurs when Google Play Services (GMS) fails to verify the calling package's identity, or when there is a mismatch/misconfiguration in the development environment.

## User Review Required

> [!IMPORTANT]
> The project currently uses `compileSdk = 37` and `agp = "9.3.1"`. Both are experimental/preview versions. I recommend downgrading to stable versions (`compileSdk = 35` and `agp = 8.x`) to ensure library compatibility, especially with Google Play Services.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///E:/AndroidStudioProjects/FridaySTM/app/build.gradle.kts)
- Downgrade `compileSdk` from `37` to `35` to match the latest stable Android version and improve compatibility with Play Services.
- Verify `namespace` and `applicationId` consistency.

#### [MODIFY] [libs.versions.toml](file:///E:/AndroidStudioProjects/FridaySTM/gradle/libs.versions.toml)
- Downgrade `playServicesLocation` from `21.3.0` to `21.0.1` as a diagnostic step, as recent versions have reported similar `SecurityException` issues in certain environments.
- Downgrade `agp` to a stable version if the user approves, but I will start with `compileSdk` and library versions first.

### Manifest

#### [MODIFY] [AndroidManifest.xml](file:///E:/AndroidStudioProjects/FridaySTM/app/src/main/AndroidManifest.xml)
- Explicitly add the `package` attribute to the `<manifest>` tag (if not present) and a `<queries>` block for `com.google.android.gms` to ensure the OS and GMS can correctly resolve the interaction.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds with the new SDK and library versions.

### Manual Verification
- Deploy the app to the device/emulator.
- Observe the logs (Logcat) to see if the `GoogleApiManager` error persists when initializing location services or auth.
- Verify that location fetching (which uses GMS) works without throwing the `SecurityException`.
