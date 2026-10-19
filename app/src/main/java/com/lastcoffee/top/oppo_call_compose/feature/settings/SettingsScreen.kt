package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.ConfigurationPackage
import com.lastcoffee.top.oppo_call_compose.data.ManualCallStore
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.RingbackBadgeStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    calls: ManualCallStore,
    extras: PhoneExtrasStore,
    badges: RingbackBadgeStore,
    onBack: () -> Unit,
    onAddCallRecord: () -> Unit,
    onOpenCallRules: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenRingback: () -> Unit,
    onOpenAutoHangUp: () -> Unit,
    onOpenPromptNumbers: (CallPromptType) -> Unit,
) {
    val context = LocalContext.current
    val packager = remember(context, calls, extras, badges) { ConfigurationPackage(context, calls, extras, badges) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                packager.exportTo(uri)
                message = "配置包已保存"
            } catch (error: Exception) {
                message = error.message ?: "导出失败"
            } finally { busy = false }
        }
    }

    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("设置", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val file = packager.createShareFile()
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "通话配置", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "分享通话配置包"))
                        } catch (error: Exception) {
                            message = error.message ?: "分享失败"
                        } finally { busy = false }
                    }
                }, enabled = !busy) {
                    Icon(Icons.Filled.Share, contentDescription = "分享配置包", tint = Color(0xFF242831))
                }
                IconButton(onClick = { saveLauncher.launch("通话配置.zip") }, enabled = !busy) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = "导出配置包到文件", tint = Color(0xFF242831))
                }
            }
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenCallRules)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("通话规则与音频", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onAddCallRecord)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("添加通话记录", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenLocations)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("号码归属地", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenRingback)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("彩铃设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenAutoHangUp)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("自动挂断号码", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
            }
            CallPromptType.entries.forEach { type ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .clickable { onOpenPromptNumbers(type) }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(type.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF858585))
                }
            }
            }
        }
    }
    message?.let { result ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("配置包") },
            text = { Text(result) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } },
        )
    }
}
