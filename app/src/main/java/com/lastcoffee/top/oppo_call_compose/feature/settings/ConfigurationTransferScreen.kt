package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.lastcoffee.top.oppo_call_compose.data.ConfigurationPackage
import com.lastcoffee.top.oppo_call_compose.data.ManualCallStore
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.PreparedConfiguration
import com.lastcoffee.top.oppo_call_compose.data.RingbackBadgeStore
import kotlinx.coroutines.launch

@Composable
fun ConfigurationTransferScreen(
    calls: ManualCallStore,
    extras: PhoneExtrasStore,
    badges: RingbackBadgeStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val packager = remember(context, calls, extras, badges) { ConfigurationPackage(context, calls, extras, badges) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<PreparedConfiguration?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                packager.exportTo(uri)
                message = "配置包已保存，可以通过文件管理器发送给其他用户"
            } catch (error: Exception) {
                message = error.message ?: "导出失败"
            } finally { busy = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                prepared?.discard()
                prepared = packager.prepareImport(uri)
            } catch (error: Exception) {
                message = error.message ?: "配置包无法读取"
            } finally { busy = false }
        }
    }
    BackHandler(enabled = busy || prepared != null) {
        if (!busy) { prepared?.discard(); prepared = null }
    }

    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("配置分享与导入", fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp))
            }
            Text("配置包包含通话规则、归属地与号码绑定、彩铃图标规则，以及已导入的音频、视频和自定义图标。配置包会包含设置过的手机号；不包含通话记录。",
                fontSize = 15.sp, lineHeight = 23.sp, color = Color(0xFF555B64))
            Button(onClick = {
                scope.launch {
                    busy = true
                    message = null
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
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("分享配置包") }
            OutlinedButton(onClick = { saveLauncher.launch("通话配置.zip") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("保存配置包到文件") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("从文件导入配置包") }
            Text("导入会替换本机的通话规则、彩铃媒体、图标规则和号码设置。导入前会先显示内容摘要；本机通话记录不会被改动。",
                fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF666B73))
            if (busy) CircularProgressIndicator()
            message?.let { Text(it, color = Color(0xFF356E25), fontSize = 14.sp) }
        }
    }

    prepared?.let { candidate ->
        AlertDialog(
            onDismissRequest = { candidate.discard(); prepared = null },
            title = { Text("导入此配置包？") },
            text = { Text("包含 ${candidate.summary.rules} 条通话状态规则、${candidate.summary.media} 个媒体文件、${candidate.summary.icons} 个自定义图标，以及约 ${candidate.summary.numbers} 项号码设置。导入后将替换本机对应配置。") },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    prepared = null
                    scope.launch {
                        message = null
                        try {
                            packager.applyImport(candidate)
                            message = "导入完成，规则和媒体文件已可使用"
                        } catch (error: Exception) {
                            message = error.message ?: "导入失败"
                            candidate.discard()
                        } finally { busy = false }
                    }
                }) { Text("确认导入") }
            },
            dismissButton = { TextButton(onClick = { candidate.discard(); prepared = null }) { Text("取消") } },
        )
    }
}
