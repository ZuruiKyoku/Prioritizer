package com.slygames.prioritizer.ui.theme

import androidx.compose.ui.graphics.Color

// These gradients are for priority display only.
// Do not use as general theme colors—use MaterialTheme.colorScheme for the rest of the UI.

//Light Mode
val LightPriorityColors = listOf(
    Color(0xFF81C784), // 1 - Soft green
    Color(0xFFA5D06F), // 2 - Light yellow-green
    Color(0xFFD4E157), // 3 - Gentle chartreuse
    Color(0xFFFFF176), // 4 - Soft lemon yellow
    Color(0xFFFFE082), // 5 - Warm yellow, slightly gold
    Color(0xFFFFC260), // 6 - Amber
    Color(0xFFFFA040), // 7 - Orange
    Color(0xFFFF7043), // 8 - Orange-red
    Color(0xFFEC4C47), // 9 - Muted red
    Color(0xFFD32F2F)  // 10 - Strong red
)
val LightForeColor = Color(0xFF1A1A1A)
val LightCompletedBGColor = Color(0xFFF2F5F8)
val LightCompletedForeColor = Color(0xFF7A7A7A)

//Dark Mode
val DarkPriorityColors = listOf(
    Color(0xFF3CB371), // 1 - Medium Sea Green
    Color(0xFF53B768), // 2 - Slightly lighter green
    Color(0xFF7FBF3F), // 3 - Yellow-green (keep)
    Color(0xFF95A738), // 4 - Muted olive
    Color(0xFFD1B843), // 5 - Soft gold
    Color(0xFFFFA540), // 6 - Slightly muted amber
    Color(0xFFFF8540), // 7 - Soft orange
    Color(0xFFFA6744), // 8 - Orange-red
    Color(0xFFE84C3D), // 9 - Red-orange
    Color(0xFFC0392B)  // 10 - Strong red
)
val DarkForeColor = Color(0xFF1A1A1A)
val DarkCompletedBGColor = Color(0xFF232730)
val DarkCompletedForeColor = Color(0xFFB5B9BE)