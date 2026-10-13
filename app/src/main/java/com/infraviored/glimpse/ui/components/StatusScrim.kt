package com.infraviored.glimpse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.infraviored.glimpse.ui.theme.glimpse

/** Keeps the clock and icons readable over pictures scrolling beneath the status bar. */
@Composable
fun StatusScrim(modifier: Modifier = Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(top + 14.dp)
            .background(
                Brush.verticalGradient(
                    0f to glimpse.background.copy(alpha = 0.94f),
                    0.72f to glimpse.background.copy(alpha = 0.8f),
                    1f to Color.Transparent,
                ),
            ),
    )
}
