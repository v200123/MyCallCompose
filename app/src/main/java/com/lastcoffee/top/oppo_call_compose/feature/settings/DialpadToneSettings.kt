package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

@Composable
internal fun DialpadToneSettings(extras: PhoneExtrasStore) {
    val settings by extras.settings.collectAsState()
    DialpadToneSettingsRow(settings.dialpadToneEnabled, extras::setDialpadToneEnabled)
}

@Composable
internal fun DialpadToneSettingsRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("播放拨号音", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("按数字、* 或 # 时播放系统按键音；静音或振动模式下不播放", fontSize = 14.sp, color = Color(0xFF858585))
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Preview(name = "播放拨号音开启", showBackground = true, widthDp = 540)
@Composable
private fun DialpadToneSettingsEnabledPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        DialpadToneSettingsRow(true, {})
    }
}

@Preview(name = "播放拨号音关闭", showBackground = true, widthDp = 360)
@Composable
private fun DialpadToneSettingsDisabledPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        DialpadToneSettingsRow(false, {})
    }
}
