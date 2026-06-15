plugins {
    alias(libs.plugins.android.application)
    // Kotlin is provided by AGP 9's built-in Kotlin support. KSP 2.3.6+ is compatible with it.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "tv.own.owntv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "tv.own.owntv"
        minSdk = 26
        targetSdk = 36
        // CI injects these from the git tag (see .github/workflows/android.yml) so releases never
        // need a manual edit here. The fallbacks are only used for local/debug builds.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        // Keep the fallback at the latest released version, or local debug builds will see the
        // in-app updater offer them the release as an "update".
        versionName = System.getenv("VERSION_NAME") ?: "99.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is driven by env vars (set from GitHub secrets in CI). When they're absent
    // — local dev, debug builds, or unsigned CI — nothing here applies, so builds still work.
    val releaseKeystore = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)

    // Compose for TV
    implementation(libs.androidx.tv.material)

    // Lifecycle / Navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // Database (Room, via KSP) + Paging
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Networking
    implementation(libs.okhttp)

    // Media playback — libmpv (FFmpeg) engine
    implementation(libs.libmpv)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
