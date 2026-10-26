package com.lastcoffee.top.oppo_call_compose.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** 16.22.0 call_button_circle.xml: 66dp, #29999999; selected fills white. */
@Composable
internal fun InCallButtonSurface(
    modifier: Modifier = Modifier,
    diameter: Dp = 66.dp,
    selected: Boolean = false,
    customColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val background by animateColorAsState(
        targetValue = customColor ?: if (selected) Color.White else Color(0x29999999),
        animationSpec = tween(150),
        label = "call button selection",
    )
    Box(
        modifier = modifier
            .size(diameter)
            .then(Modifier.clip(CircleShape))
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val fill = Brush.radialGradient(
                    0f to Color(0xFF17221D).copy(alpha = 0.77f),
                    0.70f to Color(0xFF34423B).copy(alpha = 0.74f),
                    1f to Color(0xFF9CA9A3).copy(alpha = 0.39f),
                    center = center,
                    radius = radius,
                )
                val rim = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.28f)),
                )
                onDrawBehind {
                    if (selected || customColor != null) drawCircle(background, radius)
                    else drawCircle(fill, radius)
                    if (!selected && customColor == null) drawCircle(rim, radius - 0.7.dp.toPx(), style = Stroke(1.1.dp.toPx()))
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
            .background(Color(0xFF030B09))
            .drawWithCache {
                val upperGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF577766).copy(alpha = 0.63f), Color(0xFF183D2E).copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.58f, size.height * 0.02f),
                    radius = size.width * 0.90f,
                )
                val sideGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF436753).copy(alpha = 0.68f), Color(0xFF0F2F24).copy(alpha = 0.44f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.42f),
                    radius = size.width * 0.76f,
                )
                onDrawBehind {
                    drawRect(upperGlow)
                    drawRect(sideGlow)
                }
            },
    )
}
