package com.lastcoffee.top.oppo_call_compose.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.CallKind

// com.android.contacts 16.80.15: values/colors.xml and values/dimens.xml.
// Use Android dp directly; this source does not declare a 412dp AutoSize canvas.
internal val OppoPrimary = Color(0xE6000000)
internal val OppoSecondary = Color(0x8A000000)
internal val OppoTertiary = Color(0x42000000)
internal val OppoDividerColor = Color(0x1F000000)
internal val OppoCardPage = Color(0xFFF0F1F2)
internal val OppoGreen = Color(0xFF2FB152)
internal val OppoDividerHeight = 0.33.dp

internal fun oppoTextStyle(
    size: TextUnit,
    color: Color = OppoPrimary,
    weight: FontWeight = FontWeight.Normal,
    includeFontPadding: Boolean = true,
) = TextStyle(
    fontSize = size,
    color = color,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
)

@Composable
internal fun OppoDivider(start: Dp, end: Dp, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = start, end = end),
        thickness = OppoDividerHeight,
        color = OppoDividerColor,
    )
}

internal val CallKind.oppoIcon: Int
    get() = when (this) {
        CallKind.INCOMING -> R.drawable.oppo_pb_ic_call_incoming
        CallKind.OUTGOING -> R.drawable.oppo_pb_ic_call_outgoing
        CallKind.MISSED -> R.drawable.oppo_pb_ic_call_missed
        CallKind.BLOCKED -> R.drawable.oppo_pb_ic_call_declined
    }
