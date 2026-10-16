package com.lastcoffee.top.oppo_call_compose.feature.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lastcoffee.top.oppo_call_compose.data.BadgeAsset
import com.lastcoffee.top.oppo_call_compose.data.BadgePlacement
import com.lastcoffee.top.oppo_call_compose.data.BadgeRule

/** The same normalized canvas is used by the editor and during video playback. */
@Composable
fun RingbackBadgeCanvas(
    rule: BadgeRule?,
    modifier: Modifier = Modifier,
    selected: BadgeAsset? = null,
    onSelect: ((BadgeAsset) -> Unit)? = null,
    onChange: ((BadgePlacement) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val canvasWidth = maxWidth.value
        val canvasHeight = maxHeight.value
        val density = LocalDensity.current
        val latestRule by rememberUpdatedState(rule)
        Box(Modifier.fillMaxSize()) {
            rule?.placements?.sortedBy { it.zIndex }?.forEach { placement ->
                val asset = placement.asset
                val width = asset.widthDp * placement.scale
                val height = asset.heightDp * placement.scale
                val left = (placement.xPercent * canvasWidth - width / 2f).coerceIn(0f, (canvasWidth - width).coerceAtLeast(0f))
                val top = (placement.yPercent * canvasHeight - height / 2f).coerceIn(0f, (canvasHeight - height).coerceAtLeast(0f))
                val imageModifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
                    .then(if (selected == asset) Modifier.background(Color(0x334AA3FF)) else Modifier)
                    .then(if (onChange != null) Modifier.pointerInput(asset, canvasWidth, canvasHeight) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val current = latestRule?.placements?.firstOrNull { it.asset == asset } ?: return@detectTransformGestures
                            val panDp = with(density) { Offset(pan.x.toDp().value, pan.y.toDp().value) }
                            onChange(current.copy(
                                xPercent = (current.xPercent + panDp.x / canvasWidth).coerceIn(0f, 1f),
                                yPercent = (current.yPercent + panDp.y / canvasHeight).coerceIn(0f, 1f),
                                scale = (current.scale * zoom).coerceIn(0.2f, 2f),
                            ))
                        }
                    } else Modifier)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(asset) } else Modifier)
                Image(painterResource(asset.drawable), contentDescription = asset.title, modifier = imageModifier)
            }
        }
    }
}
