package com.slygames.prioritizer.ui.theme

import androidx.compose.ui.graphics.Color

fun priorityBackgroundColor(priority: Int, isDarkMode: Boolean): Color {
    val safePriority = priority.coerceIn(1, 10)
    return if (isDarkMode) DarkPriorityColors[safePriority - 1]
    else LightPriorityColors[safePriority - 1]
}

fun priorityForeColor(isDarkMode: Boolean): Color {
    return if (isDarkMode) DarkForeColor
    else LightForeColor
}

fun priorityCompletedBGColor(isDarkMode: Boolean): Color {
    return if (isDarkMode) DarkCompletedBGColor
    else LightCompletedBGColor
}

fun priorityCompletedForeColor(isDarkMode: Boolean): Color {
    return if (isDarkMode) DarkCompletedForeColor
    else LightCompletedForeColor
}