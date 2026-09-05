import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Secrets kept out of source (SKILL.md §8): put the following in local.properties
// (git-ignored), or pass -P Gradle props / env vars.
//   cloudinary.cloudName=xxx
//   cloudinary.uploadPreset=yyy
val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }

fun secretProp(key: String, env: String): String =
    (project.findProperty(key) as String?)
        ?: localProps.getProperty(key)
        ?: System.getenv(env).orEmpty()

val cloudinaryCloudName: String = secretProp("cloudinary.cloudName", "CLOUDINARY_CLOUD_NAME")
val cloudinaryUploadPreset: String = secretProp("cloudinary.uploadPreset", "CLOUDINARY_UPLOAD_PRESET")

android {
    namespace = "com.gynda.fridaystm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gynda.fridaystm"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Cloudinary unsigned upload target — read at runtime via BuildConfig, so
        // no keys are hard-coded in source (SKILL.md §8).
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // --- Compose / AndroidX (existing) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Lifecycle + Compose interop (collectAsStateWithLifecycle, viewModel()) ---
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Navigation Compose ---
    implementation(libs.androidx.navigation.compose)

    // --- Firebase (BOM manages versions) ---
    // No firebase-storage: selfie evidence goes to Cloudinary (see CloudinaryUploader).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // --- Coroutines (+ Firebase Task.await()) ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // --- Location (geofencing) ---
    implementation(libs.play.services.location)

    // --- CameraX (selfie capture) ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- Image loading (Coil 3) ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // --- Cloudinary unsigned upload (OkHttp multipart) ---
    implementation(libs.okhttp)

    // --- Mini-map (osmdroid: free, no API key) ---
    implementation(libs.osmdroid.android)

    // CameraX's ProcessCameraProvider.getInstance() returns a Guava ListenableFuture;
    // only the empty "avoid-conflict" stub is on the classpath transitively, so Guava
    // is added to actually supply the ListenableFuture class (CameraPreview.kt).
    implementation(libs.guava)

    // --- Unit test ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // --- Instrumented / UI test ---
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}