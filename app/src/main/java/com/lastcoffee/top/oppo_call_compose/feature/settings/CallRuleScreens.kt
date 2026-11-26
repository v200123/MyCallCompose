package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.CallNumberPoolEntity
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.BUILT_IN_WAITING_TONE_ID
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusDisplayMode
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.REMOTE_HANGUP_STATUS_ID
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.defaultDisplayText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CallModeSettingsScreen(
    config: CallFlowConfiguration,
    onBack: () -> Unit,
    onSelectMode: (CallAudioMode) -> Unit,
    onOpenRules: () -> Unit,
    onOpenDefaultAudio: () -> Unit,
    onOpenLibrary: (CallAudioMode) -> Unit,
) {
    SettingsFrame("通话规则与音频", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("新号码默认归入所选音频池；号码已存在时沿用原归属。", color = Color(0xFF707782), modifier = Modifier.padding(horizontal = 16.dp))
            CallAudioMode.entries.filter { it.isCallMode }.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                        .background(Color.White, RoundedCornerShape(16.dp)).clickable { onSelectMode(mode) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = config.selectedAudioMode == mode, onClick = { onSelectMode(mode) })
                    Text(mode.title, modifier = Modifier.weight(1f), fontSize = 17.sp)
                    TextButton(onClick = { onOpenLibrary(mode) }) { Text("管理库") }
                }
            }
            SettingsLink("状态规则", "所有通话共用", onOpenRules)
            SettingsLink("默认音频", "按通话状态试听内置音频", onOpenDefaultAudio)
        }
    }
}

private val builtInStatuses = defaultCallStatuses()
private val builtInStatusIds = builtInStatuses.mapTo(mutableSetOf(), CallStatusRule::id)
private val CallStatusRule.settingsTitle: String
    get() = if (id == REMOTE_HANGUP_STATUS_ID) "对方挂断" else name

@Composable
internal fun DefaultCallAudioScreen(mediaEntries: List<CallMediaEntry>, mode: CallAudioMode, onBack: () -> Unit) {
    val entriesById = remember(mediaEntries) { mediaEntries.associateBy(CallMediaEntry::id) }
    SettingsFrame("默认音频", onBack) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(builtInStatuses, key = CallStatusRule::id) { status ->
                val audioId = when {
                    status.stage == CallStage.WAITING -> BUILT_IN_WAITING_TONE_ID
                    status.playSoundOnce -> status.soundEntryId
                    else -> status.mediaEntryId
                }
                val entry = entriesById[audioId] ?: return@items
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Text(status.settingsTitle, Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.Medium)
                        CallMediaPreviewButton(
                            rulePreviewEntries(status, mediaEntries, PhoneExtras(), mode).map {
                                it.copy(entry = it.entry.copy(title = status.settingsTitle))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallRulesScreen(
    config: CallFlowConfiguration,
    extras: PhoneExtras,
    mediaEntries: List<CallMediaEntry>,
    onAddMedia: suspend (CallMediaEntry) -> Unit,
    onBack: () -> Unit,
    onSave: suspend (List<CallStatusRule>) -> Unit,
) {
    var current by remember { mutableStateOf(config.statuses) }
    LaunchedEffect(config.statuses) { current = config.statuses }
    var editing by remember { mutableStateOf<CallStatusRule?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(statuses: List<CallStatusRule>) {
        if (saving) return
        saving = true
        saveError = null
        scope.launch {
            try {
                onSave(statuses)
                current = statuses
                editing = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = "规则保存失败，请重试"
            } finally {
                saving = false
            }
        }
    }

    BackHandler(enabled = saving) {}
    SettingsFrame("状态规则", onBack = { if (!saving) onBack() }) {
        if (editing == null) saveError?.let {
            Text(it, color = Color(0xFFB3261E), modifier = Modifier.padding(horizontal = 18.dp))
        }
        Text("可调整各状态在通话界面的显示方式、持续时长和音频。", color = Color(0xFF707782), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(current, key = { it.id }) { status ->
                val categoryCount = CallPromptType.entries.count { extras.promptRuleFor(it) == status.id }
                val bindingCount = extras.promptNumbers.keys.count {
                    extras.promptRuleFor(it) == status.id
                }
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val remoteHangup = status.id == REMOTE_HANGUP_STATUS_ID
                                Text(status.settingsTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                val durationLabel = when {
                                    status.durationSeconds > 0 -> "${status.durationSeconds} 秒"
                                    status.stage == CallStage.DISCONNECTING || status.stage == CallStage.DISCONNECTED -> "立即完成"
                                    else -> "不限时"
                                }
                                Text("${if (remoteHangup) status.name else status.stage.title} · $durationLabel", color = Color(0xFF707782))
                                if (status.stage == CallStage.WAITING) {
                                    Text("无人接听和忙线先按号码模式响铃，其他提示直接播放", color = Color(0xFF707782), fontSize = 12.sp)
                                }
                                val audioName = if (status.playSoundOnce) {
                                    if (status.soundMuted) "静音" else mediaEntries.firstOrNull { it.id == status.soundEntryId }?.title ?: "未选择提示音"
                                } else mediaEntries.firstOrNull { it.id == status.mediaEntryId }?.title ?: "号码音频池随机"
                                val playCount = if (status.playSoundOnce) "播放一次" else when (status.audioPlayCount) {
                                    0 -> "无限循环"
                                    1 -> "播放一次"
                                    else -> "播放 ${status.audioPlayCount} 次"
                                }
                                Text("$audioName · $playCount", color = Color(0xFF707782), fontSize = 12.sp)
                            }
                            TextButton(onClick = { saveError = null; editing = status }, enabled = !saving) { Text("编辑") }
                            if (status.id !in builtInStatusIds && bindingCount == 0 && categoryCount == 0 && current.none { other ->
                                    other.id != status.id && (other.timeoutTargetId == status.id || other.buttons.any { it.targetStatusId == status.id })
                                }) {
                                IconButton(
                                    onClick = { save(current.filterNot { it.id == status.id }) },
                                    enabled = !saving,
                                ) { Icon(Icons.Default.Delete, contentDescription = "删除状态") }
                            }
                        }
                        CallMediaPreviewButton(
                            entries = rulePreviewEntries(status, mediaEntries, extras, config.selectedAudioMode,
                                waitingRule = current.firstOrNull { it.stage == CallStage.WAITING }),
                            enabled = !saving,
                        )
                        if (bindingCount > 0) Text("已绑定 $bindingCount 个号码", color = Color(0xFF707782), fontSize = 12.sp)
                        if (categoryCount > 0) Text("用于 $categoryCount 类提示音", color = Color(0xFF707782), fontSize = 12.sp)
                    }
                }
            }
        }
    }
    editing?.let { status ->
        StatusEditorDialog(
            initial = status, mediaEntries = mediaEntries, extras = extras, onAddMedia = onAddMedia,
            mode = config.selectedAudioMode,
            waitingRule = current.firstOrNull { it.stage == CallStage.WAITING },
            saving = saving,
            saveError = saveError,
            onDismiss = { editing = null; saveError = null },
            onApply = { updated -> save(current.map { if (it.id == updated.id) updated else it }) },
        )
    }
}

@Composable
private fun StatusEditorDialog(
    initial: CallStatusRule,
    mediaEntries: List<CallMediaEntry>,
    extras: PhoneExtras,
    mode: CallAudioMode,
    waitingRule: CallStatusRule?,
    onAddMedia: suspend (CallMediaEntry) -> Unit,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onApply: (CallStatusRule) -> Unit,
) {
    val canEditName = initial.id != REMOTE_HANGUP_STATUS_ID
    var name by remember(initial) { mutableStateOf(initial.name) }
    var displayText by remember(initial) { mutableStateOf(initial.displayText.orEmpty()) }
    var displayMode by remember(initial) { mutableStateOf(initial.displayMode) }
    var displayModeMenu by remember { mutableStateOf(false) }
    var duration by remember(initial) { mutableStateOf(initial.durationSeconds.toString()) }
    var useDefaultPromptWait by remember(initial) { mutableStateOf(initial.useDefaultPromptWait) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var mediaEntryId by remember(initial) { mutableStateOf(initial.mediaEntryId) }
    var mediaMenu by remember { mutableStateOf(false) }
    var soundEntryId by remember(initial) { mutableStateOf(initial.soundEntryId) }
    var playSoundOnce by remember(initial) { mutableStateOf(initial.playSoundOnce) }
    var soundMuted by remember(initial) { mutableStateOf(initial.soundMuted) }
    var repeatAudio by remember(initial) { mutableStateOf(initial.audioPlayCount != 1) }
    var audioPlayCount by remember(initial) { mutableStateOf(initial.audioPlayCount.toString()) }
    val selectableMedia = mediaEntries.filter {
        (it.mode.isCallMode || (initial.stage in setOf(CallStage.OUTCOME, CallStage.VOICEMAIL, CallStage.ACTIVE) && it.mode == CallAudioMode.STATUS_SOUND)) &&
            (initial.id != REMOTE_HANGUP_STATUS_ID || it.mimeType.startsWith("audio/")) &&
            it.mode.acceptsMimeType(it.mimeType)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            importError = null
            scope.launch {
                try {
                    val imported = CallMediaFiles.importDocument(context, uri, "status_sounds")
                    if (imported == null) {
                        importError = "音效导入失败"
                        return@launch
                    }
                    if (!imported.mimeType.startsWith("audio/")) {
                        CallMediaFiles.delete(context, imported.path)
                        importError = "请选择音频文件"
                        return@launch
                    }
                    val entry = CallMediaEntry(
                        id = UUID.randomUUID().toString(),
                        mode = CallAudioMode.STATUS_SOUND,
                        title = imported.title,
                        filePath = imported.path,
                        mimeType = imported.mimeType,
                    )
                    try {
                        onAddMedia(entry)
                        soundEntryId = entry.id
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        CallMediaFiles.delete(context, imported.path)
                        importError = "音效保存失败"
                    }
                } finally {
                    importing = false
                }
            }
        }
    }
    val busy = saving || importing
    val durationSeconds = duration.toIntOrNull()?.takeIf { it in 0..86_400 }
    val playCount = if (playSoundOnce || !repeatAudio) 1 else audioPlayCount.toIntOrNull()
    val draft = if (durationSeconds != null && playCount != null) {
        initial.copy(
            name = if (canEditName) name.ifBlank { "未命名状态" } else initial.name,
            displayText = displayText.trim().ifEmpty { null },
            displayMode = displayMode,
            durationSeconds = durationSeconds,
            useDefaultPromptWait = useDefaultPromptWait,
            mediaEntryId = mediaEntryId,
            soundEntryId = soundEntryId,
            playSoundOnce = playSoundOnce,
            soundMuted = soundMuted,
            audioPlayCount = playCount,
        )
    } else null
    val editedRule = draft ?: initial
    val defaultText = editedRule.defaultDisplayText()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("状态规则") },
        text = {
            LazyColumn {
                if (canEditName) item {
                    OutlinedTextField(name, { name = it }, label = { Text("状态名称") }, singleLine = true)
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("界面显示方式", modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { displayModeMenu = true }, enabled = !busy) { Text(displayMode.title) }
                            DropdownMenu(expanded = displayModeMenu, onDismissRequest = { displayModeMenu = false }) {
                                CallStatusDisplayMode.entries.forEach { mode ->
                                    DropdownMenuItem(text = { Text(mode.title) }, onClick = {
                                        displayMode = mode
                                        displayModeMenu = false
                                    })
                                }
                            }
                        }
                    }
                    if (displayMode != CallStatusDisplayMode.TIMER_ONLY) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = { displayText = it },
                            label = { Text("通话界面文字") },
                            supportingText = {
                                Text(if (defaultText != editedRule.name) "留空显示「$defaultText」" else "留空时沿用该状态的默认显示")
                            },
                            singleLine = true,
                            enabled = !busy,
                        )
                    }
                    if (displayMode == CallStatusDisplayMode.TIMER_ONLY || displayMode == CallStatusDisplayMode.TEXT_AND_TIMER) {
                        Text("接通前显示当前状态用时，接通后显示通话时长。", color = Color(0xFF707782),
                            fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                item {
                    OutlinedTextField(
                        duration, {
                            val updated = it.filter(Char::isDigit).take(5)
                            if (updated != duration) useDefaultPromptWait = false
                            duration = updated
                        },
                        label = { Text("状态时长（秒）") },
                        isError = durationSeconds == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !busy,
                    )
                }
                (saveError ?: importError)?.let { error ->
                    item { Text(error, color = Color(0xFFB3261E)) }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = playSoundOnce, onCheckedChange = { playSoundOnce = it })
                            Text("提示音模式：进入状态时播放一次")
                        }
                        if (playSoundOnce) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = soundMuted, onCheckedChange = { soundMuted = it })
                                Text("静音")
                                if (!soundMuted) {
                                    mediaEntries.firstOrNull { it.id == soundEntryId && it.mimeType.startsWith("audio/") }?.let { sound ->
                                        Text(sound.title, modifier = Modifier.weight(1f), color = Color(0xFF707782), fontSize = 12.sp, maxLines = 1)
                                    }
                                    TextButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }, enabled = !busy) {
                                        Text(if (soundEntryId == null) "上传音效" else "更换音效")
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("状态期间音频", modifier = Modifier.weight(1f))
                                Box {
                                    TextButton(onClick = { mediaMenu = true }) {
                                        Text(selectableMedia.firstOrNull { it.id == mediaEntryId }?.title ?: "号码音频池随机")
                                    }
                                    DropdownMenu(mediaMenu, { mediaMenu = false }) {
                                        DropdownMenuItem(text = { Text("号码音频池随机") }, onClick = { mediaEntryId = null; mediaMenu = false })
                                        selectableMedia.forEach { media ->
                                            DropdownMenuItem(
                                                text = { Text(media.mode.title + " · " + media.title) },
                                                onClick = { mediaEntryId = media.id; mediaMenu = false },
                                            )
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = repeatAudio, onCheckedChange = { repeatAudio = it })
                                Text("音频循环播放")
                            }
                            if (repeatAudio) OutlinedTextField(
                                value = audioPlayCount,
                                onValueChange = { audioPlayCount = it.filter(Char::isDigit).take(2) },
                                label = { Text("音频播放总次数（0 为无限循环）") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                    }
                }
                if (draft != null) item {
                    CallMediaPreviewButton(
                        entries = rulePreviewEntries(draft, mediaEntries, extras, mode, waitingRule),
                        enabled = !busy,
                    )
                }
                item {
                    Text(
                        "状态顺序由程序决定。普通状态时长为 0 时不按时间推进；挂断步骤的 0 秒表示立即完成。",
                        color = Color(0xFF707782), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && draft != null, onClick = {
                draft?.let(onApply)
            }) { Text(if (saving) "保存中…" else if (importing) "导入中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
fun CallMediaLibraryScreen(
    mode: CallAudioMode,
    entries: List<CallMediaEntry>,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onAdd: suspend (CallMediaEntry) -> Unit,
    onUpdate: suspend (CallMediaEntry) -> Unit,
    onDelete: suspend (CallMediaEntry) -> Unit,
) {
    val context = LocalContext.current
    var iconTarget by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = iconTarget; iconTarget = null
        if (uri != null && target != null) scope.launch {
            importError = null
            CallMediaFiles.importDocument(context, uri, "icons")?.let { imported ->
                val entry = entries.firstOrNull { it.id == target }
                if (entry == null) CallMediaFiles.delete(context, imported.path)
                else {
                    try {
                        onUpdate(entry.copy(iconPath = imported.path))
                        CallMediaFiles.delete(context, entry.iconPath)
                    } catch (error: Exception) {
                        CallMediaFiles.delete(context, imported.path)
                        importError = "图标保存失败"
                    }
                }
            } ?: run { importError = "图标复制失败" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importError = null
            val imported = CallMediaFiles.importDocument(context, uri, mode.name.lowercase())
                ?: run { importError = "文件复制失败"; return@launch }
            if (!mode.acceptsMimeType(imported.mimeType)) {
                CallMediaFiles.delete(context, imported.path)
                importError = "文件格式不符合${mode.title}要求"
                return@launch
            }
            val previous = if (mode == CallAudioMode.DEFAULT_TONE) entries.filter { it.mode == mode && it.id != BUILT_IN_WAITING_TONE_ID } else emptyList()
            try {
                onAdd(CallMediaEntry(UUID.randomUUID().toString(), mode, imported.title, imported.path, imported.mimeType))
            } catch (error: Exception) {
                CallMediaFiles.delete(context, imported.path)
                importError = "媒体保存失败"
                return@launch
            }
            previous.forEach { old -> runCatching { onDelete(old) } }
        }
    }
    val modeEntries = entries.filter { it.mode == mode }
    val visibleEntries = if (mode == CallAudioMode.DEFAULT_TONE && modeEntries.any { it.id != BUILT_IN_WAITING_TONE_ID }) {
        modeEntries.filter { it.id != BUILT_IN_WAITING_TONE_ID }
    } else modeEntries
    SettingsFrame("${mode.title}库", onBack, footer = {
        Button(onClick = { picker.launch(if (mode == CallAudioMode.RINGBACK) arrayOf("video/*") else arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("导入文件") }
    }) {
        Text(
            text = when (mode) {
                CallAudioMode.DEFAULT_TONE -> "等待接听时播放。可以导入一段铃声；删除后恢复内置铃声。"
                CallAudioMode.RINGBACK -> "彩铃仅支持视频，可添加多个。"
                CallAudioMode.MUSIC -> "音乐仅支持音频，可添加多个。"
                CallAudioMode.STATUS_SOUND -> "状态提示音仅支持音频。"
                CallAudioMode.DIALPAD_TONE -> "拨号按键音仅支持音频。"
            },
            color = Color(0xFF707782),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text("选择后会复制到应用私有目录，删除原文件不会影响播放。", color = Color(0xFF707782),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        importError?.let { Text(it, color = Color(0xFFB3261E), modifier = Modifier.padding(horizontal = 16.dp)) }
        if (entries.none { it.mode == mode && mode.acceptsMimeType(it.mimeType) }) Text("库中还没有符合格式的内容；模拟拨号时将继续状态规则。", color = Color(0xFF707782), modifier = Modifier.padding(20.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(visibleEntries, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val customIcon = remember(item.iconPath) { item.iconPath?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() } }
                        if (customIcon != null) Image(customIcon, contentDescription = "${item.title}图标", modifier = Modifier.size(48.dp))
                        else Icon(when { item.mimeType.startsWith("video/") -> Icons.Default.Movie; item.mimeType.startsWith("audio/") -> Icons.Default.MusicNote; else -> Icons.Default.AudioFile }, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(item.title, fontWeight = FontWeight.Medium)
                            Text(item.mimeType, color = Color(0xFF707782), fontSize = 12.sp)
                            if (mode == CallAudioMode.RINGBACK) TextButton(onClick = { iconTarget = item.id; iconPicker.launch(arrayOf("image/*")) }) { Text(if (item.iconPath == null) "设置自定义图标" else "更换图标") }
                        }
                        CallMediaPreviewButton(listOf(CallMediaPreview(item)))
                        if (item.id != BUILT_IN_WAITING_TONE_ID) {
                            IconButton(onClick = { scope.launch { onDelete(item) } }) { Icon(Icons.Default.Delete, contentDescription = "删除媒体") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallNumberPoolsScreen(
    entries: List<CallNumberPoolEntity>,
    config: CallFlowConfiguration,
    extras: PhoneExtras,
    extrasStore: PhoneExtrasStore,
    mediaEntries: List<CallMediaEntry>,
    onBack: () -> Unit,
    onAddNumber: (String) -> Unit,
    onChangeMode: (String, CallAudioMode) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CallNumberPoolEntity?>(null) }
    SettingsFrame("自动操作规则绑定", onBack) {
        Text("在这里添加号码，并设置提示类型、自动挂断时间等号码行为。每类提示音的规则在对应号码页面统一选择。", modifier = Modifier.padding(16.dp), color = Color(0xFF707782))
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("添加号码") }
        if (entries.isEmpty()) Text("暂无号码", modifier = Modifier.padding(20.dp), color = Color(0xFF707782))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(entries, key = { it.number }) { entry ->
                val variant = extras.screenVariantFor(entry.number)
                val promptType = extras.promptFor(entry.number)
                val prompt = promptType?.let { type ->
                    val ruleName = config.statuses.firstOrNull { it.id == extras.promptRuleFor(entry.number) }?.name ?: "规则已删除"
                    "${type.title.removeSuffix("号码")} → $ruleName"
                }
                val auto = extras.autoHangUpSecondsFor(entry.number)
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { editing = entry }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.number, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull("${variant.title}界面", CallAudioMode.fromStored(entry.mode).title, prompt,
                            auto?.let { "接通后 ${it} 秒自动挂断" }).joinToString(" · "), color = Color(0xFF707782), fontSize = 13.sp)
                        mediaEntries.firstOrNull { it.id == entry.waitingMediaId }?.let {
                            Text("固定音频：${it.title}", color = Color(0xFF707782), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var raw by remember { mutableStateOf("") }
        val normalized = normalizePhoneNumber(raw)
        val valid = normalized.length in 3..20 && entries.none { normalizePhoneNumber(it.number) == normalized }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("添加号码到号码池") },
            text = { Column {
                OutlinedTextField(raw, { raw = it }, label = { Text("电话号码（3–20 位）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                if (raw.isNotBlank() && !valid) Text(if (entries.any { normalizePhoneNumber(it.number) == normalized }) "号码已在号码池中" else "请输入 3–20 位电话号码", color = Color.Red)
            } },
            confirmButton = { TextButton(onClick = { onAddNumber(normalized); adding = false }, enabled = valid) { Text("添加") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } })
    }
    editing?.let { entry ->
        NumberPoolBindingEditor(entry, config, extras, onDismiss = { editing = null }) { variant, mode, prompt, seconds ->
            extrasStore.setScreenVariant(entry.number, variant)
            onChangeMode(entry.number, mode)
            if (prompt == null) extrasStore.deletePromptNumber(entry.number)
            else extrasStore.savePromptNumber(entry.number, prompt)
            if (seconds == null) extrasStore.deleteAutoHangUpRule(entry.number) else extrasStore.saveAutoHangUpRule(entry.number, seconds)
            editing = null
        }
    }
}

@Composable
private fun NumberPoolBindingEditor(
    entry: CallNumberPoolEntity,
    config: CallFlowConfiguration,
    extras: PhoneExtras,
    onDismiss: () -> Unit,
    onSave: (CallScreenVariant, CallAudioMode, CallPromptType?, Int?) -> Unit,
) {
    var variant by remember(entry.number) { mutableStateOf(extras.screenVariantFor(entry.number)) }
    var mode by remember(entry.number) { mutableStateOf(CallAudioMode.fromStored(entry.mode)) }
    var prompt by remember(entry.number) { mutableStateOf(extras.promptFor(entry.number)) }
    var secondsText by remember(entry.number) { mutableStateOf(extras.autoHangUpSecondsFor(entry.number)?.toString().orEmpty()) }
    var modeMenu by remember { mutableStateOf(false) }
    var variantMenu by remember { mutableStateOf(false) }
    var promptMenu by remember { mutableStateOf(false) }
    val seconds = secondsText.toIntOrNull()
    val validSeconds = secondsText.isBlank() || (seconds != null && seconds in 1..3600)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("配置 ${entry.number}") },
        text = { Column {
            Box { TextButton(onClick = { variantMenu = true }) { Text("通话界面：${variant.title}") }
                DropdownMenu(variantMenu, { variantMenu = false }) {
                    CallScreenVariant.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = {
                            variant = option; variantMenu = false
                        })
                    }
                }
            }
            Box { TextButton(onClick = { modeMenu = true }) { Text("音频模式：${mode.title}") }
                DropdownMenu(modeMenu, { modeMenu = false }) {
                    CallAudioMode.entries.filter { it.isCallMode }.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = { mode = option; modeMenu = false })
                    }
                }
            }
            Box { TextButton(onClick = { promptMenu = true }) { Text("提示类型：${prompt?.title ?: "无"}") }
                DropdownMenu(promptMenu, { promptMenu = false }) {
                    DropdownMenuItem(text = { Text("无") }, onClick = { prompt = null; promptMenu = false })
                    CallPromptType.entries.forEach { type -> DropdownMenuItem(text = { Text(type.title) }, onClick = {
                        prompt = type; promptMenu = false
                    }) }
                }
            }
            if (prompt != null) {
                val categoryRuleId = extras.promptRuleFor(requireNotNull(prompt))
                val categoryRuleName = config.statuses.firstOrNull { it.id == categoryRuleId && requireNotNull(prompt).acceptsRule(it) }?.name ?: "未设置有效规则"
                Text("${requireNotNull(prompt).title}统一使用：$categoryRuleName", color = Color.Gray, fontSize = 12.sp)
            }
            OutlinedTextField(secondsText, { secondsText = it.filter(Char::isDigit).take(4) },
                label = { Text("接通后自动挂断秒数（留空关闭）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            if (!validSeconds) Text("请输入 1–3600 秒", color = Color.Red)
        } },
        confirmButton = { TextButton(onClick = { onSave(variant, mode, prompt, seconds) },
            enabled = validSeconds) { Text("保存绑定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun SettingsFrame(title: String, onBack: () -> Unit, footer: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), content = content)
            footer?.invoke()
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(Color.White, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(subtitle, color = Color(0xFF707782), fontSize = 13.sp) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
