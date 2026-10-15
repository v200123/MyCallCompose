package com.lastcoffee.top.oppo_call_compose.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The OPPO call buttons use a translucent 66dp circle with a soft inner light/shadow treatment. */
@Composable
internal fun InCallButtonSurface(
    modifier: Modifier = Modifier,
    diameter: Dp = 66.dp,
    selected: Boolean = false,
    customColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .then(Modifier.clip(CircleShape))
            .background(customColor ?: InCallButtonBackground)
            .drawWithCache {
                val buttonBrush = Brush.verticalGradient(
                    0f to Color(0xFF48524F).copy(alpha = 0.68f),
                    0.34f to Color(0xFF252D2A).copy(alpha = 0.72f),
                    1f to Color(0xFF101815).copy(alpha = 0.84f),
                )
                val highlightBrush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (selected) 0.30f else 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.35f, size.height * 0.22f),
                    radius = size.minDimension * 0.82f,
                )
                val edgeBrush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.30f)),
                )
                onDrawBehind {
                    if (customColor == null) {
                        drawCircle(buttonBrush)
                        drawCircle(highlightBrush)
                    } else {
                        drawCircle(customColor)
                    }
                    if (selected) drawCircle(Color.White.copy(alpha = 0.20f))
                    drawCircle(edgeBrush, style = Stroke(width = 1.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun InCallPreviewBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020907))
            .drawWithCache {
                val upperGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF0D553A).copy(alpha = 0.72f), Color(0xFF06301F).copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.54f, size.height * 0.14f),
                    radius = size.minDimension * 0.83f,
                )
                val sideGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF0C3828).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(size.width * 0.98f, size.height * 0.30f),
                    radius = size.minDimension * 0.52f,
                )
                onDrawBehind {
                    drawRect(upperGlow)
                    drawRect(sideGlow)
                }
            },
    )
}
