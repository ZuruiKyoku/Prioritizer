plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.slygames.prioritizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.slygames.prioritizer"
        minSdk = 24
        targetSdk = 35
        versionCode = 11
        versionName = "Beta 1.0"
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
}
