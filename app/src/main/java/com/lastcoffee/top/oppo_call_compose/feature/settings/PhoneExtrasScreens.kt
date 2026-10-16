package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.CustomPhoneLocation
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.isMainlandMobilePrefix
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import java.io.File

@Composable
fun CustomLocationsScreen(settings: PhoneExtras, store: PhoneExtrasStore, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<CustomPhoneLocation?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteNumber by remember { mutableStateOf<String?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    ExtrasFrame("自定义号码归属地", onBack) {
        Text("中国大陆手机号可填前 7 位，匹配同号段号码；完整号码优先。省份、城市、运营商至少填写一项。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("添加归属地") }
        if (settings.locations.isEmpty()) Text("还没有自定义归属地", modifier = Modifier.padding(20.dp), color = Color.Gray)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(settings.locations.values.sortedBy { it.number }, key = { it.number }) { location ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Row(Modifier.clickable { editing = location; showEditor = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isMainlandMobilePrefix(location.number)) "${location.number} · 手机号段" else location.number, fontWeight = FontWeight.SemiBold)
                            Text(location.displayText, color = Color.Gray)
                        }
                        IconButton(onClick = { deleteNumber = location.number }) { Icon(Icons.Default.Delete, contentDescription = "删除归属地") }
                    }
                }
            }
        }
        if (settings.locations.isNotEmpty()) TextButton(onClick = { clearAll = true }, modifier = Modifier.fillMaxWidth()) { Text("清空所有归属地") }
    }
    if (showEditor) LocationEditor(editing, onDismiss = { showEditor = false }, onSave = {
        store.saveLocation(it)
        showEditor = false
    })
    deleteNumber?.let { number ->
        ConfirmDelete("删除 $number 的自定义归属地？", onDismiss = { deleteNumber = null }) {
            store.deleteLocation(number); deleteNumber = null
        }
    }
    if (clearAll) ConfirmDelete("清空所有自定义归属地？", onDismiss = { clearAll = false }) {
        store.clearLocations(); clearAll = false
    }
}

@Composable
private fun LocationEditor(existing: CustomPhoneLocation?, onDismiss: () -> Unit, onSave: (CustomPhoneLocation) -> Unit) {
    var number by remember(existing) { mutableStateOf(existing?.number.orEmpty()) }
    var province by remember(existing) { mutableStateOf(existing?.province.orEmpty()) }
    var city by remember(existing) { mutableStateOf(existing?.city.orEmpty()) }
    var carrier by remember(existing) { mutableStateOf(existing?.carrier.orEmpty()) }
    val normalized = normalizePhoneNumber(number)
    val valid = normalized.length in 3..15 && listOf(province, city, carrier).any { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加归属地" else "编辑归属地") },
        text = {
            Column {
                OutlinedTextField(number, { number = it.filter(Char::isDigit).take(15) },
                    label = { Text("手机号前 7 位或完整号码") }, singleLine = true,
                    enabled = existing == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Text("例如 1380013 可匹配所有以它开头的大陆手机号", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(province, { province = it }, label = { Text("省份") }, singleLine = true)
                OutlinedTextField(city, { city = it }, label = { Text("城市") }, singleLine = true)
                OutlinedTextField(carrier, { carrier = it }, label = { Text("运营商") }, singleLine = true)
                if (number.isNotBlank() && normalized.length !in 3..15) Text("请输入 3 到 15 位号码", color = Color.Red)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(CustomPhoneLocation(normalized, province.trim(), city.trim(), carrier.trim())) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun RingbackSettingsScreen(
    settings: PhoneExtras,
    store: PhoneExtrasStore,
    mediaEntries: List<CallMediaEntry>,
    onOpenLibrary: () -> Unit,
    onOpenAudioMode: () -> Unit,
    onOpenBadgeRules: () -> Unit,
    onBack: () -> Unit,
) {
    val videos = mediaEntries.filter { it.mode == CallAudioMode.RINGBACK && it.mimeType.startsWith("video/") && File(it.filePath).isFile }
    var selectingDefault by remember { mutableStateOf(false) }
    var bindingNumbers by remember { mutableStateOf(false) }
    var blockingNumbers by remember { mutableStateOf(false) }
    var deleteBinding by remember { mutableStateOf<String?>(null) }
    var deleteBlocked by remember { mutableStateOf<String?>(null) }
    ExtrasFrame("彩铃设置", onBack) {
        Text("号码绑定会直接用于拨号；默认彩铃用于彩铃模式。不播放名单优先于所有彩铃。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("管理彩铃视频") }
        Button(onClick = onOpenBadgeRules, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("自定义彩铃图标规则") }
        TextButton(onClick = onOpenAudioMode, modifier = Modifier.padding(start = 12.dp)) { Text("设置新号码的拨打播放模式") }
        ExtrasRow("默认彩铃", videos.firstOrNull { it.id == settings.defaultRingbackId }?.title ?: "随机使用视频库", onClick = { selectingDefault = true })
        Text("号码绑定", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 18.dp))
        TextButton(onClick = { bindingNumbers = true }, modifier = Modifier.padding(start = 12.dp)) { Text("添加或批量绑定号码") }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(settings.ringbackBindings.toList().sortedBy { it.first }, key = { it.first }) { (number, id) ->
                ExtrasRow(number, videos.firstOrNull { it.id == id }?.title ?: "彩铃已删除", onClick = { bindingNumbers = true }, onDelete = { deleteBinding = number })
            }
            item { Text("不播放彩铃的号码", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 18.dp)) }
            item { TextButton(onClick = { blockingNumbers = true }, modifier = Modifier.padding(start = 12.dp)) { Text("添加号码") } }
            items(settings.noRingbackNumbers.sorted(), key = { it }) { number ->
                ExtrasRow(number, "拨号时仅播放等待音", onClick = { deleteBlocked = number }, onDelete = { deleteBlocked = number })
            }
        }
    }
    if (selectingDefault) VideoPicker(videos, includeRandom = true, onDismiss = { selectingDefault = false }) {
        store.setDefaultRingback(it); selectingDefault = false
    }
    if (bindingNumbers) NumberVideoEditor(videos, onDismiss = { bindingNumbers = false }) { numbers, id ->
        numbers.forEach { store.bindRingback(it, id) }
        bindingNumbers = false
    }
    if (blockingNumbers) NumberListEditor("添加不播放彩铃的号码", onDismiss = { blockingNumbers = false }) { numbers ->
        numbers.forEach { store.setRingbackBlocked(it, true) }
        blockingNumbers = false
    }
    deleteBinding?.let { number -> ConfirmDelete("解绑 $number 的彩铃？", onDismiss = { deleteBinding = null }) { store.bindRingback(number, null); deleteBinding = null } }
    deleteBlocked?.let { number -> ConfirmDelete("从不播放名单移除 $number？", onDismiss = { deleteBlocked = null }) { store.setRingbackBlocked(number, false); deleteBlocked = null } }
}

@Composable
private fun NumberVideoEditor(videos: List<CallMediaEntry>, onDismiss: () -> Unit, onSave: (List<String>, String) -> Unit) {
    var raw by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(videos.firstOrNull()?.id) }
    var showPicker by remember { mutableStateOf(false) }
    val numbers = parseNumbers(raw)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定彩铃号码") },
        text = { Column {
            OutlinedTextField(raw, { raw = it }, label = { Text("号码，支持换行或逗号分隔") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextButton(onClick = { showPicker = true }, enabled = videos.isNotEmpty()) { Text(videos.firstOrNull { it.id == selectedId }?.title ?: "请先导入彩铃视频") }
            Text("识别到 ${numbers.size} 个号码", color = Color.Gray)
        } },
        confirmButton = { TextButton(onClick = { selectedId?.let { onSave(numbers, it) } }, enabled = numbers.isNotEmpty() && selectedId != null) { Text("绑定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showPicker) VideoPicker(videos, includeRandom = false, onDismiss = { showPicker = false }) { selectedId = it; showPicker = false }
}

@Composable
private fun NumberListEditor(title: String, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var raw by remember { mutableStateOf("") }
    val numbers = parseNumbers(raw)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column {
            OutlinedTextField(raw, { raw = it }, label = { Text("号码，支持换行或逗号分隔") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text("识别到 ${numbers.size} 个号码", color = Color.Gray)
        } },
        confirmButton = { TextButton(onClick = { onSave(numbers) }, enabled = numbers.isNotEmpty()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun parseNumbers(raw: String): List<String> = raw.split(Regex("[,，;；\\s]+"))
    .map(::normalizePhoneNumber).filter { it.length in 3..15 }.distinct()

@Composable
private fun VideoPicker(videos: List<CallMediaEntry>, includeRandom: Boolean, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择彩铃视频") },
        text = { LazyColumn {
            if (includeRandom) item { Text("随机使用视频库", modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(12.dp)) }
            items(videos, key = { it.id }) { video -> Text(video.title, modifier = Modifier.fillMaxWidth().clickable { onSelect(video.id) }.padding(12.dp)) }
            if (videos.isEmpty()) item { Text("请先导入彩铃视频") }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ConfirmDelete(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("确认操作") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun ExtrasFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
internal fun ExtrasRow(title: String, subtitle: String, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .background(Color.White, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Color.Gray, fontSize = 13.sp) }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
    }
}
