package com.slygames.prioritizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * All framework/singleton wiring (Room, Firebase, repositories) now lives in Hilt modules under
 * `di/` -- this class no longer manually constructs a shared ViewModel or database instance (that
 * used to cause duplicate Firestore listeners whenever a screen built its own separate
 * MainViewModel instead of reusing this one).
 */
@HiltAndroidApp class PrioritizerApp : Application()
