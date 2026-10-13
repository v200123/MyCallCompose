package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.CallNumberPoolEntity
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallButtonId
import com.lastcoffee.top.oppo_call_compose.model.CallButtonRule
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.CallVisualStyle
import com.lastcoffee.top.oppo_call_compose.model.defaultButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CallModeSettingsScreen(
    config: CallFlowConfiguration,
    onBack: () -> Unit,
    onSelectMode: (CallAudioMode) -> Unit,
    onOpenRules: () -> Unit,
    onOpenPools: () -> Unit,
    onOpenLibrary: (CallAudioMode) -> Unit,
) {
    SettingsFrame("通话规则与音频", onBack) {
        Text("新号码默认归入所选音频池；号码已存在时沿用原归属。", color = Color(0xFF707782), modifier = Modifier.padding(horizontal = 16.dp))
        CallAudioMode.entries.filter { it != CallAudioMode.STATUS_SOUND }.forEach { mode ->
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
        SettingsLink("状态规则", "${config.statuses.size} 个状态", onOpenRules)
        SettingsLink("号码池", "按铃声模式查看和调整归属", onOpenPools)
    }
}

private val fixedStageStatusIds = setOf("dialing", "waiting", "no_answer", "busy", "empty_number", "powered_off", "active", "hold", "disconnecting", "disconnected")

@Composable
fun CallRulesScreen(
    config: CallFlowConfiguration,
    mediaEntries: List<CallMediaEntry>,
    onAddMedia: suspend (CallMediaEntry) -> Unit,
    onBack: () -> Unit,
    onSave: (CallFlowConfiguration) -> Unit,
) {
    var current by remember(config) { mutableStateOf(config) }
    var editing by remember { mutableStateOf<CallStatusRule?>(null) }
    var isNew by remember { mutableStateOf(false) }
    SettingsFrame("状态规则", onBack, footer = {
        Button(onClick = { isNew = true; editing = CallStatusRule(UUID.randomUUID().toString(), "新状态", CallStage.WAITING) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("新增状态") }
    }) {
        Text("设为入口", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(current.statuses, key = { it.id }) { status ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(status.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text("${status.stage.title} · ${if (status.durationSeconds > 0) "${status.durationSeconds} 秒" else "不自动计时"}", color = Color(0xFF707782))
                            }
                            RadioButton(current.initialStatusId == status.id, onClick = {
                                current = current.copy(initialStatusId = status.id); onSave(current)
                            })
                            TextButton(onClick = { editing = status }) { Text("编辑") }
                            if (status.id !in fixedStageStatusIds) {
                                IconButton(onClick = {
                                    current = current.copy(statuses = current.statuses.filterNot { it.id == status.id }, initialStatusId = current.initialStatusId.takeUnless { it == status.id } ?: "dialing")
                                    onSave(current)
                                }) { Icon(Icons.Default.Delete, contentDescription = "删除状态") }
                            }
                        }
                        status.timeoutTargetId?.let { target -> Text("到期跳转：${current.statuses.firstOrNull { it.id == target }?.name ?: target}", color = Color(0xFF707782)) }
                    }
                }
            }
        }
    }
    editing?.let { status ->
        StatusEditorDialog(
            initial = status, statuses = current.statuses, mediaEntries = mediaEntries, onAddMedia = onAddMedia, stageEditable = isNew || status.id !in fixedStageStatusIds, onDismiss = { editing = null },
            onApply = { updated ->
                current = if (isNew) current.copy(statuses = current.statuses + updated) else current.copy(statuses = current.statuses.map { if (it.id == updated.id) updated else it })
                editing = null; isNew = false; onSave(current)
            },
        )
    }
}

@Composable
private fun StatusEditorDialog(
    initial: CallStatusRule,
    statuses: List<CallStatusRule>,
    mediaEntries: List<CallMediaEntry>,
    onAddMedia: suspend (CallMediaEntry) -> Unit,
    stageEditable: Boolean,
    onDismiss: () -> Unit,
    onApply: (CallStatusRule) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var stage by remember(initial) { mutableStateOf(initial.stage) }
    var duration by remember(initial) { mutableStateOf(initial.durationSeconds.toString()) }
    var timeoutTarget by remember(initial) { mutableStateOf(initial.timeoutTargetId) }
    var mediaEntryId by remember(initial) { mutableStateOf(initial.mediaEntryId) }
    var mediaMenu by remember { mutableStateOf(false) }
    var soundEntryId by remember(initial) { mutableStateOf(initial.soundEntryId) }
    var playSoundOnce by remember(initial) { mutableStateOf(initial.playSoundOnce) }
    var soundMuted by remember(initial) { mutableStateOf(initial.soundMuted) }
    var callerName by remember(initial) { mutableStateOf(initial.callerName) }
    var callerNumber by remember(initial) { mutableStateOf(initial.callerNumber) }
    var locationText by remember(initial) { mutableStateOf(initial.locationText) }
    var callerNameStyle by remember(initial) { mutableStateOf(initial.callerNameStyle) }
    var callerNumberStyle by remember(initial) { mutableStateOf(initial.callerNumberStyle) }
    var locationTextStyle by remember(initial) { mutableStateOf(initial.locationTextStyle) }
    var statusTextStyle by remember(initial) { mutableStateOf(initial.statusTextStyle) }
    var visualEditorVisible by remember(initial) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = CallMediaFiles.importDocument(context, uri, "status_sounds") ?: return@launch
            if (!imported.mimeType.startsWith("audio/")) return@launch
            val entry = CallMediaEntry(
                id = UUID.randomUUID().toString(),
                mode = CallAudioMode.STATUS_SOUND,
                title = imported.title,
                filePath = imported.path,
                mimeType = imported.mimeType,
            )
            onAddMedia(entry)
            soundEntryId = entry.id
        }
    }
    var buttons by remember(initial) { mutableStateOf(initial.buttons.ifEmpty { defaultButtons(initial.stage) }) }
    var stageMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("状态规则") },
        text = {
            LazyColumn {
                item { OutlinedTextField(name, { name = it }, label = { Text("状态名称") }, singleLine = true) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("状态类型", modifier = Modifier.weight(1f))
                        if (stageEditable) Box {
                            TextButton(onClick = { stageMenu = true }) { Text(stage.title) }
                            DropdownMenu(stageMenu, { stageMenu = false }) { CallStage.entries.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { stage = item; buttons = defaultButtons(item); stageMenu = false }) } }
                        } else Text(stage.title, color = Color(0xFF707782))
                    }
                }
                item { OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(5) }, label = { Text("状态/音频时长（秒，0 表示不计时）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
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
                                    TextButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }) {
                                        Text(if (soundEntryId == null) "上传音效" else "更换音效")
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("状态期间音频", modifier = Modifier.weight(1f))
                                Box {
                                    TextButton(onClick = { mediaMenu = true }) {
                                        Text(mediaEntries.firstOrNull { it.id == mediaEntryId && it.mode != CallAudioMode.STATUS_SOUND && it.mode.acceptsMimeType(it.mimeType) }?.title ?: "号码音频池随机")
                                    }
                                    DropdownMenu(mediaMenu, { mediaMenu = false }) {
                                        DropdownMenuItem(text = { Text("号码音频池随机") }, onClick = { mediaEntryId = null; mediaMenu = false })
                                        mediaEntries.filter { it.mode != CallAudioMode.STATUS_SOUND && it.mode.acceptsMimeType(it.mimeType) }.forEach { media ->
                                            DropdownMenuItem(
                                                text = { Text(media.mode.title + " · " + media.title) },
                                                onClick = { mediaEntryId = media.id; mediaMenu = false },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("到期跳转", modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { targetMenu = true }) { Text(statuses.firstOrNull { it.id == timeoutTarget }?.name ?: "停留") }
                            DropdownMenu(targetMenu, { targetMenu = false }) {
                                DropdownMenuItem(text = { Text("停留") }, onClick = { timeoutTarget = null; targetMenu = false })
                                statuses.filterNot { it.id == initial.id }.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { timeoutTarget = item.id; targetMenu = false }) }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { visualEditorVisible = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text("打开通话页可视化编辑")
                    }
                    Text("在全屏通话页中点击文字或按钮进行修改", color = Color(0xFF707782), fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val normalizedButtons = CallButtonId.entries.map { id -> buttons.firstOrNull { it.id == id } ?: CallButtonRule(id) }
            onApply(initial.copy(name = name.ifBlank { "未命名状态" }, stage = stage, durationSeconds = duration.toIntOrNull()?.coerceIn(0, 86_400) ?: 0, timeoutTargetId = timeoutTarget, buttons = normalizedButtons, mediaEntryId = mediaEntryId, soundEntryId = soundEntryId, playSoundOnce = playSoundOnce, soundMuted = soundMuted, callerName = callerName, callerNumber = callerNumber, locationText = locationText, callerNameStyle = callerNameStyle, callerNumberStyle = callerNumberStyle, locationTextStyle = locationTextStyle, statusTextStyle = statusTextStyle))
        }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (visualEditorVisible) {
        CallScreenVisualEditor(
            statusName = name,
            callerName = callerName,
            callerNumber = callerNumber,
            locationText = locationText,
            callerNameStyle = callerNameStyle,
            callerNumberStyle = callerNumberStyle,
            locationTextStyle = locationTextStyle,
            statusTextStyle = statusTextStyle,
            stage = stage,
            buttons = buttons,
            statuses = statuses.filterNot { it.id == initial.id },
            onTextChange = { element, value, style ->
                when (element) {
                    CallTextElement.CALLER_NAME -> { callerName = value; callerNameStyle = style }
                    CallTextElement.CALLER_NUMBER -> { callerNumber = value; callerNumberStyle = style }
                    CallTextElement.LOCATION -> { locationText = value; locationTextStyle = style }
                    CallTextElement.STATUS -> { name = value; statusTextStyle = style }
                }
            },
            onButtonChange = { changed -> buttons = buttons.map { if (it.id == changed.id) changed else it } },
            onDismiss = { visualEditorVisible = false },
        )
    }
}

@Composable
private fun CallScreenVisualEditor(
    statusName: String,
    callerName: String,
    callerNumber: String,
    locationText: String,
    callerNameStyle: CallVisualStyle,
    callerNumberStyle: CallVisualStyle,
    locationTextStyle: CallVisualStyle,
    statusTextStyle: CallVisualStyle,
    stage: CallStage,
    buttons: List<CallButtonRule>,
    statuses: List<CallStatusRule>,
    onTextChange: (CallTextElement, String, CallVisualStyle) -> Unit,
    onButtonChange: (CallButtonRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingText by remember { mutableStateOf<CallTextElement?>(null) }
    var editingButton by remember { mutableStateOf<CallButtonId?>(null) }
    val currentRule = buttons.firstOrNull { it.id == editingButton } ?: editingButton?.let { CallButtonRule(it) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF26313D)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues()).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                    Column(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 92.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                EditableCallText(callerName.ifBlank { "联系人姓名" }, callerNameStyle, 32f, Color.White, true) { editingText = CallTextElement.CALLER_NAME }
                                EditableCallText(callerNumber.ifBlank { "138 0013 8000" }, callerNumberStyle, 16f, Color.White, false, 4) { editingText = CallTextElement.CALLER_NUMBER }
                                EditableCallText(locationText.ifBlank { "未知归属地" }, locationTextStyle, 14f, Color(0x99FFFFFF), false, 3) { editingText = CallTextElement.LOCATION }
                            }
                            Box(Modifier.padding(start = 16.dp).size(70.dp).clip(CircleShape).background(Color(0xFF535353)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = "联系人头像", tint = Color.White, modifier = Modifier.size(46.dp))
                            }
                        }
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            EditableCallText(statusName.ifBlank { "未命名状态" }, statusTextStyle, 18f, Color.White, false) { editingText = CallTextElement.STATUS }
                            Text("  00:18", color = Color.White, fontSize = 18.sp)
                        }
                    }
                    CallScreenButtonCanvas(stage, buttons, onSelect = { editingButton = it })
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp)) { Text("完成", color = Color.White) }
            }
        }
    }
    editingText?.let { element ->
        val value = when (element) { CallTextElement.CALLER_NAME -> callerName; CallTextElement.CALLER_NUMBER -> callerNumber; CallTextElement.LOCATION -> locationText; CallTextElement.STATUS -> statusName }
        val style = when (element) { CallTextElement.CALLER_NAME -> callerNameStyle; CallTextElement.CALLER_NUMBER -> callerNumberStyle; CallTextElement.LOCATION -> locationTextStyle; CallTextElement.STATUS -> statusTextStyle }
        val label = element.title
        val defaultColor = if (element == CallTextElement.LOCATION) Color(0x99FFFFFF) else Color.White
        CallVisualElementDialog(
            title = label, text = value, style = style, defaultColor = defaultColor,
            onDismiss = { editingText = null },
            onSave = { updatedText, updatedStyle, _, _, _ -> onTextChange(element, updatedText, updatedStyle); editingText = null },
        )
    }
    if (currentRule != null) {
        CallVisualElementDialog(
            title = currentRule.label?.takeIf { it.isNotBlank() } ?: buttonDefaultLabel(currentRule.id, stage),
            text = currentRule.label?.takeIf { it.isNotBlank() } ?: buttonDefaultLabel(currentRule.id, stage), style = currentRule.visualStyle, defaultColor = Color.White,
            visible = currentRule.visible, enabled = currentRule.enabled, targetStatusId = currentRule.targetStatusId,
            statuses = statuses, buttonId = currentRule.id,
            onDismiss = { editingButton = null },
            onSave = { newLabel, style, isVisible, isEnabled, target -> onButtonChange(currentRule.copy(label = newLabel, visualStyle = style, visible = isVisible, enabled = isEnabled, targetStatusId = target)); editingButton = null },
        )
    }
}

@Composable
private fun CallScreenButtonCanvas(stage: CallStage, buttons: List<CallButtonRule>, onSelect: (CallButtonId) -> Unit) {
    val outgoing = stage == CallStage.DIALING || stage == CallStage.WAITING
    val indexedRules = CallButtonId.entries.associateWith { id -> buttons.firstOrNull { it.id == id } ?: CallButtonRule(id) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val iconById = mapOf(
            CallButtonId.MUTE to Icons.Filled.MicOff,
            CallButtonId.SPEAKER to Icons.Filled.VolumeUp,
            CallButtonId.KEYPAD to Icons.Filled.Dialpad,
            CallButtonId.SECONDARY_ONE to Icons.Filled.Pause,
            CallButtonId.SECONDARY_TWO to if (outgoing) Icons.Filled.VideoCall else Icons.Filled.FiberManualRecord,
            CallButtonId.SECONDARY_THREE to if (outgoing) Icons.Filled.MoreVert else Icons.Filled.Call,
        )
        listOf(CallButtonId.entries.take(3), CallButtonId.entries.subList(3, 6)).forEach { rowIds ->
            Row(Modifier.fillMaxWidth().padding(top = if (rowIds.first() == CallButtonId.MUTE) 0.dp else 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowIds.forEach { id ->
                    val rule = indexedRules.getValue(id)
                    val defaultLabel = buttonDefaultLabel(id, stage)
                    InCallEditorButton(rule, defaultLabel, iconById.getValue(id)) { onSelect(id) }
                }
            }
        }
        val hangup = indexedRules.getValue(CallButtonId.HANGUP)
        InCallEditorButton(hangup, "挂断", Icons.Filled.CallEnd, hangup = true, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 30.dp, bottom = 20.dp)) { onSelect(CallButtonId.HANGUP) }
    }
}

private enum class CallTextElement(val title: String) { CALLER_NAME("联系人姓名"), CALLER_NUMBER("显示号码"), LOCATION("归属地"), STATUS("状态文字") }

private fun buttonDefaultLabel(id: CallButtonId, stage: CallStage): String {
    val outgoing = stage == CallStage.DIALING || stage == CallStage.WAITING
    return when (id) {
        CallButtonId.MUTE -> "静音"
        CallButtonId.SPEAKER -> "扬声器"
        CallButtonId.KEYPAD -> "拨号盘"
        CallButtonId.SECONDARY_ONE -> if (outgoing) "暂停" else "保持"
        CallButtonId.SECONDARY_TWO -> if (outgoing) "视频" else "录音"
        CallButtonId.SECONDARY_THREE -> if (outgoing) "更多" else "添加通话"
        CallButtonId.HANGUP -> "挂断"
    }
}

@Composable
private fun EditableCallText(value: String, style: CallVisualStyle, defaultSize: Float, defaultColor: Color, bold: Boolean, top: Int = 0, onClick: () -> Unit) {
    val hiddenColor = style.textColorArgb?.let { Color(it.toULong()) } ?: defaultColor
    Text(value, color = if (style.visible) hiddenColor else hiddenColor.copy(alpha = 0.38f), fontSize = style.fontSizeSp?.sp ?: defaultSize.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.padding(top = top.dp).offset(style.offsetXdp.dp, style.offsetYdp.dp).background(style.backgroundColorArgb?.let { Color(it.toULong()) } ?: Color.Transparent, RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 1.dp), maxLines = 1)
}

@Composable
private fun InCallEditorButton(rule: CallButtonRule, defaultLabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector, hangup: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = rule.visualStyle
    val label = rule.label?.takeIf { it.isNotBlank() } ?: defaultLabel
    Column(modifier.width(88.dp).offset(style.offsetXdp.dp, style.offsetYdp.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (hangup) 70.dp else 66.dp).clip(CircleShape).background(style.backgroundColorArgb?.let { Color(it.toULong()) } ?: if (hangup && rule.enabled) Color(0xFFE74D4D) else Color(0x29999999)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = style.textColorArgb?.let { Color(it.toULong()) } ?: if (rule.enabled) Color.White else Color(0x66FFFFFF), modifier = Modifier.size(36.dp))
            if (!rule.visible) Text("隐藏", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
        Text(label, color = style.textColorArgb?.let { Color(it.toULong()) } ?: if (rule.enabled) Color.White else Color(0x66FFFFFF), fontSize = style.fontSizeSp?.sp ?: 12.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun CallVisualElementDialog(
    title: String,
    text: String,
    style: CallVisualStyle,
    defaultColor: Color,
    visible: Boolean = style.visible,
    enabled: Boolean? = null,
    targetStatusId: String? = null,
    statuses: List<CallStatusRule> = emptyList(),
    buttonId: CallButtonId? = null,
    onDismiss: () -> Unit,
    onSave: (String, CallVisualStyle, Boolean, Boolean, String?) -> Unit,
) {
    var editedText by remember(title, text) { mutableStateOf(text) }
    var fontSize by remember(title, style.fontSizeSp) { mutableStateOf(style.fontSizeSp?.toString().orEmpty()) }
    var x by remember(title, style.offsetXdp) { mutableStateOf(style.offsetXdp.toString()) }
    var y by remember(title, style.offsetYdp) { mutableStateOf(style.offsetYdp.toString()) }
    var textColor by remember(title, style.textColorArgb) { mutableStateOf(style.textColorArgb?.toString(16)?.uppercase().orEmpty()) }
    var backgroundColor by remember(title, style.backgroundColorArgb) { mutableStateOf(style.backgroundColorArgb?.toString(16)?.uppercase().orEmpty()) }
    var isVisible by remember(title, visible) { mutableStateOf(visible) }
    var isEnabled by remember(title, enabled) { mutableStateOf(enabled ?: true) }
    var target by remember(title, targetStatusId) { mutableStateOf(targetStatusId) }
    var targetMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑$title") },
        text = {
            LazyColumn {
                item { OutlinedTextField(editedText, { editedText = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (buttonId == null) "显示文字" else "按钮文字") }, singleLine = true) }
                item { Row { OutlinedTextField(x, { x = it.filter { c -> c == '-' || c.isDigit() }.take(4) }, modifier = Modifier.weight(1f), label = { Text("横向位置 dp") }, singleLine = true); OutlinedTextField(y, { y = it.filter { c -> c == '-' || c.isDigit() }.take(4) }, modifier = Modifier.weight(1f), label = { Text("纵向位置 dp") }, singleLine = true) } }
                item { OutlinedTextField(fontSize, { fontSize = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("字体大小 sp（留空默认 ${if (buttonId == null) "" else "12"}）") }, singleLine = true) }
                item { OutlinedTextField(textColor, { textColor = it.removePrefix("#").filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(8) }, modifier = Modifier.fillMaxWidth(), label = { Text("文字颜色 ARGB（留空默认）") }, singleLine = true) }
                item { OutlinedTextField(backgroundColor, { backgroundColor = it.removePrefix("#").filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(8) }, modifier = Modifier.fillMaxWidth(), label = { Text("背景颜色 ARGB（留空默认）") }, singleLine = true) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isVisible, { isVisible = it }); Text("显示") ; if (buttonId != null) { Checkbox(isEnabled, { isEnabled = it }); Text("启用") } } }
                if (buttonId != null) item {
                    Box {
                        TextButton(onClick = { targetMenu = true }) { Text("点击后：${statuses.firstOrNull { it.id == target }?.name ?: "执行原动作"}") }
                        DropdownMenu(targetMenu, { targetMenu = false }) {
                            DropdownMenuItem(text = { Text("执行原动作") }, onClick = { target = null; targetMenu = false })
                            statuses.forEach { status -> DropdownMenuItem(text = { Text("跳转到 ${status.name}") }, onClick = { target = status.id; targetMenu = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            fun colorValue(raw: String): Long? = raw.trim().removePrefix("#").takeIf { it.isNotBlank() }?.let { value ->
                value.toLongOrNull(16)?.let { if (value.length <= 6) (it or 0xFF000000L) else it }
            }
            val updated = style.copy(offsetXdp = x.toIntOrNull()?.coerceIn(-300, 300) ?: 0, offsetYdp = y.toIntOrNull()?.coerceIn(-300, 300) ?: 0, fontSizeSp = fontSize.toFloatOrNull()?.coerceIn(6f, 96f), textColorArgb = colorValue(textColor), backgroundColorArgb = colorValue(backgroundColor), visible = isVisible)
            onSave(editedText, updated, isVisible, isEnabled, target)
        }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = iconTarget; iconTarget = null
        if (uri != null && target != null) scope.launch {
            CallMediaFiles.importDocument(context, uri, "icons")?.let { imported -> entries.firstOrNull { it.id == target }?.let { onUpdate(it.copy(iconPath = imported.path)) } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = CallMediaFiles.importDocument(context, uri, mode.name.lowercase()) ?: return@launch
            if (!mode.acceptsMimeType(imported.mimeType)) {
                CallMediaFiles.delete(imported.path)
                return@launch
            }
            if (mode == CallAudioMode.DEFAULT_TONE) {
                entries.filter { it.mode == CallAudioMode.DEFAULT_TONE }.forEach { onDelete(it) }
            }
            onAdd(CallMediaEntry(UUID.randomUUID().toString(), mode, imported.title, imported.path, imported.mimeType))
        }
    }
    SettingsFrame("${mode.title}库", onBack, footer = {
        Button(onClick = { picker.launch(if (mode == CallAudioMode.RINGBACK) arrayOf("video/*") else arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("导入文件") }
    }) {
        Text(
            text = when (mode) {
                CallAudioMode.DEFAULT_TONE -> "普通铃声只保留一个；导入新铃声会替换当前铃声。"
                CallAudioMode.RINGBACK -> "彩铃仅支持视频，可添加多个。"
                CallAudioMode.MUSIC -> "音乐仅支持音频，可添加多个。"
                CallAudioMode.STATUS_SOUND -> "状态提示音仅支持音频。"
            },
            color = Color(0xFF707782),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (entries.none { it.mode == mode && mode.acceptsMimeType(it.mimeType) }) Text("库中还没有符合格式的内容；模拟拨号时将继续状态规则。", color = Color(0xFF707782), modifier = Modifier.padding(20.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(entries.filter { it.mode == mode }, key = { it.id }) { item ->
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
                        IconButton(onClick = { scope.launch { onDelete(item) } }) { Icon(Icons.Default.Delete, contentDescription = "删除媒体") }
                    }
                }
            }
        }
    }
}

@Composable
fun CallNumberPoolsScreen(entries: List<CallNumberPoolEntity>, onBack: () -> Unit, onChangeMode: (String, CallAudioMode) -> Unit) {
    SettingsFrame("号码池", onBack) {
        if (entries.isEmpty()) Text("号码会在模拟拨号时自动加入当前选择的池。", modifier = Modifier.padding(20.dp), color = Color(0xFF707782))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(entries, key = { it.number }) { entry ->
                var expanded by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.number, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { expanded = true }) { Text(CallAudioMode.fromStored(entry.mode).title) }
                            DropdownMenu(expanded, { expanded = false }) {
                                CallAudioMode.entries.filter { it != CallAudioMode.STATUS_SOUND }.forEach { mode -> DropdownMenuItem(text = { Text(mode.title) }, onClick = { onChangeMode(entry.number, mode); expanded = false }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsFrame(title: String, onBack: () -> Unit, footer: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
