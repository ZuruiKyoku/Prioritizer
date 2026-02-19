package com.slygames.prioritizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColors =
        lightColorScheme(
                primary = Color(0xFF3A86FF), // A modern blue
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD6E5FF), // Soft blue background for primary elements
                onPrimaryContainer = Color(0xFF002C5A),
                secondary = Color(0xFF6C757D), // Neutral secondary (cool gray)
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE1E3E6),
                onSecondaryContainer = Color(0xFF23272B),
                background = Color(0xFFF7F8FA), // Gentle off-white
                onBackground = Color(0xFF222222),
                surface = Color(0xFFFFFFFF), // True white for cards/dialogs
                onSurface = Color(0xFF23252B),
                surfaceVariant = Color(0xFFF0F1F3), // Slightly gray card for subtle surfaces
                onSurfaceVariant = Color(0xFF53575A),
                outline = Color(0xFFDADCE0),
                error = Color(0xFFD32F2F), // Material error red
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002)
        )

val DarkColors =
        darkColorScheme(
                primary = Color(0xFF61AFFF), // Brighter blue for dark mode
                onPrimary = Color(0xFF001D34),
                primaryContainer = Color(0xFF16395A),
                onPrimaryContainer = Color(0xFFD6E5FF),
                secondary = Color(0xFF7A8590), // Cool gray for dark mode
                onSecondary = Color(0xFF23272B),
                secondaryContainer = Color(0xFF32373D),
                onSecondaryContainer = Color(0xFFD6E5FF),
                background = Color(0xFF181A20), // Nearly black
                onBackground = Color(0xFFEFEFF1),
                surface = Color(0xFF23252B), // Slightly lighter card for dialogs/cards
                onSurface = Color(0xFFEFEFF1),
                surfaceVariant = Color(0xFF2B2F36), // For subtle surfaces
                onSurfaceVariant = Color(0xFFB0B3BC),
                outline = Color(0xFF444950),
                error = Color(0xFFF38BA0), // Softer error red for dark mode
                onError = Color(0xFF2B000A),
                errorContainer = Color(0xFF8C1D33),
                onErrorContainer = Color(0xFFFFDAD6)
        )

@Composable
fun PrioritizerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
    )
}
