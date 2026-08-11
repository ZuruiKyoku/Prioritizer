plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android.plugin)
}

android {
    namespace = "com.slygames.prioritizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.slygames.prioritizer"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "Beta 2.0"
        testInstrumentationRunner = "com.slygames.prioritizer.HiltTestRunner"
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

    buildFeatures { compose = true }
    composeOptions {
        kotlinCompilerExtensionVersion =
                "1.5.13" // Adjust if you know a better match for Compose BOM
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Robolectric's legacy SQLite shadow extracts a native sqlite4java.dll into
                // java.io.tmpdir on every test run. On some Windows machines, antivirus/EDR
                // blocks writing a freshly-created, unsigned .dll straight into the generic
                // %TEMP% root (AccessDeniedException), even though the same folder tree is
                // otherwise fully writable. Redirecting to a folder already inside the project's
                // own build output -- which tooling already writes to constantly -- avoids
                // whatever heuristic is triggering on raw %TEMP%\<random>\*.dll.
                val robolectricTmp = layout.buildDirectory.dir("robolectric-tmp").get().asFile
                it.doFirst { robolectricTmp.mkdirs() }
                it.systemProperty("java.io.tmpdir", robolectricTmp.absolutePath)
            }
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    // Compose BOM and core Compose modules
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.androidx.navigation.compose)
    implementation("com.google.android.material:material:1.11.0")

    // Room (Local DB)
    implementation(libs.androidx.room.runtime)
    implementation(libs.firebase.messaging.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)

    // Hilt (Dependency Injection)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase (BOM ensures versions are compatible)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.functions.ktx)

    // Google Sign-In (for Auth)
    implementation(libs.google.auth)

    // DataStore for preferences
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Json for serialization
    implementation(libs.kotlinx.serialization.json)

    // Compose Tooling (only for debug/dev)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Icons
    implementation(libs.androidx.material.icons.extended)

    // Extras
    implementation(libs.coil.compose)
    implementation(libs.exifinterface)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
    // Robolectric normally fetches this Android SDK jar itself via its own ad-hoc downloader,
    // which is flaky on Windows (fails with "Unable to create parent directories..." even with a
    // working internet connection). Declaring it as a normal Gradle dependency lets Gradle's own
    // (already-working) resolver fetch and cache it instead -- Robolectric then just finds it
    // already on the test classpath and skips its own fetch entirely.
    testRuntimeOnly("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.51.1")
}
