package com.slygames.prioritizer

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Swaps in Hilt's test application so `@HiltAndroidTest` instrumented tests get a fresh,
 * test-scoped dependency graph instead of the real [PrioritizerApp]. Wired via
 * `testInstrumentationRunner` in app/build.gradle.kts. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
