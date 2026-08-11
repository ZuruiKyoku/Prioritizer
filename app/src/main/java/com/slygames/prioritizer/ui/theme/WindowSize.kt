package com.slygames.prioritizer.ui.theme

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Exposes the current window width class app-wide (spec: layouts must adapt to screen size
 * instead of assuming a phone, without hardcoding dimensions). Provided once in MainActivity from
 * `calculateWindowSizeClass(activity)` and read by any screen that needs to adapt -- avoids
 * threading the value through every composable's parameter list.
 */
val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/** Isolated wrapper around the still-experimental `calculateWindowSizeClass` so the opt-in stays
 * in one plain (non-Hilt) file rather than needing an `@OptIn` on an `@AndroidEntryPoint` class,
 * which trips up kapt's Java stub generation for that class. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberAppWindowSizeClass(activity: Activity): WindowSizeClass = calculateWindowSizeClass(activity)

/** Phones stay edge-to-edge; tablets/unfolded foldables/landscape get their content capped to a
 * readable column width instead of stretching a phone-designed single-column layout edge to edge. */
const val COMFORTABLE_CONTENT_MAX_WIDTH_DP = 640
