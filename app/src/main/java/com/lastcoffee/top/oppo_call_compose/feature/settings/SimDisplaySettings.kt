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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore

@Composable
internal fun SimDisplaySettings(extras: PhoneExtrasStore) {
    val settings by extras.settings.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .toggleable(
                value = settings.dualSimDisplayEnabled,
                role = Role.Switch,
                onValueChange = extras::setDualSimDisplayEnabled,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("显示双卡拨号", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("检测到两张 SIM 卡时显示原有双卡按钮；现有通话记录默认标记卡1", fontSize = 14.sp, color = Color(0xFF858585))
        }
        Switch(checked = settings.dualSimDisplayEnabled, onCheckedChange = null)
    }
}
