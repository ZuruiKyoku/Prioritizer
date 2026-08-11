package com.slygames.prioritizer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slygames.prioritizer.ui.theme.COMFORTABLE_CONTENT_MAX_WIDTH_DP
import com.slygames.prioritizer.ui.theme.LocalWindowWidthSizeClass

/**
 * Caps content to a comfortable reading width on tablets/unfolded foldables/landscape instead of
 * stretching a phone-designed single-column layout edge to edge (spec: layouts must adapt to
 * screen size, not assume a phone). On phones (Compact) this is a no-op.
 */
@Composable
fun ResponsiveContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val widthSizeClass = LocalWindowWidthSizeClass.current
    if (widthSizeClass == WindowWidthSizeClass.Compact) {
        Box(modifier = modifier.fillMaxSize()) { content() }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = COMFORTABLE_CONTENT_MAX_WIDTH_DP.dp).fillMaxWidth()) {
                content()
            }
        }
    }
}
