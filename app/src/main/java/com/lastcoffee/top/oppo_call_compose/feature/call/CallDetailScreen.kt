package com.lastcoffee.top.oppo_call_compose.feature.call

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.feature.home.formatDialNumber
import com.lastcoffee.top.oppo_call_compose.feature.home.homeAttribution
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.model.displayLabel
import com.lastcoffee.top.oppo_call_compose.ui.common.MissedRed
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoCardPage
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDivider
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoGreen
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoPrimary
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoSecondary
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoTertiary
import com.lastcoffee.top.oppo_call_compose.ui.common.TagOrange
import com.lastcoffee.top.oppo_call_compose.ui.common.indicatorColor
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoIcon
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoTextStyle
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val DetailAccent = Color(0xFF247748)
private val PlaybackButtonBackground = Color(0xFFEDEDED)
private val PlaybackButtonIcon = Color(0xFF181818)
private val HistoryRowStartInset = 14.dp
private val HistoryIconSize = 16.dp
private val HistoryIconSpacing = 8.dp
private val HistoryContentInset = HistoryRowStartInset + HistoryIconSize + HistoryIconSpacing
private val HistoryEndInset = 18.dp
private val HistoryActionPadding = 12.dp
private val HistoryActionContentPadding = PaddingValues(horizontal = HistoryActionPadding, vertical = 8.dp)
private val HistoryActionTextStyle = oppoTextStyle(13.sp, Color.Unspecified, FontWeight.Medium, includeFontPadding = false)
private val RecordingThumbSize = 18.dp

@Composable
internal fun CallDetailScreen(
    record: CallRecord,
    allRecords: List<CallRecord>,
    contacts: List<ContactMatch> = emptyList(),
    customLocation: String? = null,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onEditNumber: (String) -> Unit = {},
    onDelete: suspend (CallRecord) -> Boolean = { false },
    fromContact: Boolean = false,
    onOpenRecording: ((String) -> Unit)? = null,
    recordingFileFor: (String) -> File? = { null },
    onViewRecordingFile: ((File) -> Unit)? = null,
) {
    val number = record.phoneNumber(contacts)
    val contact = remember(record, contacts) {
        contacts.firstOrNull { it.number.filter(Char::isDigit) == number || it.name == record.number }
    }
    val isEmergency = record.number == "紧急呼叫中心"
    val displayName = when {
        isEmergency -> record.number
        contact != null -> contact.name
        record.number.any(Char::isLetter) -> record.number
        else -> remember(record.number) { formatDialNumber(record.number) }
    }
    val secondaryNumber = if (contact != null && !isEmergency) {
        remember(contact.number) { formatDialNumber(contact.number) }
    } else null
    val location = customLocation ?: contact?.location ?: when {
        isEmergency -> number
        record.storedId != null -> record.homeAttribution(null)
        record.description.filterNot(Char::isWhitespace).all(Char::isDigit) -> number
        else -> record.description.substringAfter('\n').removePrefix("小布通话").trim()
    }
    val history = remember(record, allRecords, contacts) {
        allRecords.filter { it.phoneNumber(contacts) == number }.ifEmpty { if (fromContact) emptyList() else listOf(record) }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAllHistory by remember(record) { mutableStateOf(false) }
    var showDeleteDialog by remember(record) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = showAllHistory) { showAllHistory = false }

    Surface(color = OppoCardPage, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                IconButton(
                    onClick = { if (showAllHistory) showAllHistory = false else onBack() },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Image(painterResource(R.drawable.oppo_coui_back_arrow_normal), "返回", Modifier.size(24.dp))
                }
                if (showAllHistory) {
                    Text("通话记录", Modifier.align(Alignment.Center), style = oppoTextStyle(18.sp, weight = FontWeight.Medium))
                } else if (!fromContact) {
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                        if (contact == null) {
                            IconButton(onClick = {}) {
                                Image(painterResource(R.drawable.oppo_pb_dr_menu_add_normal), "添加联系人", Modifier.size(24.dp))
                            }
                        }
                        if (record.storedId != null || record.demoIndex != null) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Image(painterResource(R.drawable.oppo_coui_toolbar_menu_icon_more_normal), "更多", Modifier.size(24.dp))
                                }
                                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("编辑号码") }, onClick = {
                                        menuExpanded = false
                                        onEditNumber(number)
                                    })
                                    DropdownMenuItem(text = { Text("删除通话记录") }, onClick = {
                                        menuExpanded = false
                                        showDeleteDialog = true
                                    })
                                }
                            }
                        }
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (showAllHistory) 8.dp else 0.dp, bottom = 20.dp),
            ) {
                if (!showAllHistory) {
                    item {
                        DetailHeader(
                            displayName = displayName,
                            secondaryNumber = secondaryNumber,
                            location = location,
                            tag = record.tag?.takeUnless { isEmergency },
                            showVideo = !isEmergency,
                            onDial = { onDial(number) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .padding(start = HistoryRowStartInset, end = HistoryEndInset - HistoryActionPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "通话记录",
                                Modifier.weight(1f),
                                style = oppoTextStyle(14.sp, OppoSecondary, FontWeight.Medium, includeFontPadding = false),
                            )
                            if (history.isNotEmpty()) {
                                TextButton(
                                    onClick = { showAllHistory = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = DetailAccent),
                                    contentPadding = HistoryActionContentPadding,
                                ) { Text("查看全部", style = HistoryActionTextStyle) }
                            }
                        }
                    }
                }
                item { HistoryCard(if (showAllHistory) history else history.take(3), onOpenRecording, recordingFileFor, onViewRecordingFile) }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("要删除此通话记录吗？") },
            text = { Text(if (deleteFailed) "删除失败，请重试" else "此通话记录将从本设备彻底删除") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch { deleteFailed = !onDelete(record); deleting = false }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
}

private fun CallRecord.phoneNumber(contacts: List<ContactMatch>): String {
    val digits = number.filter(Char::isDigit)
    if (digits.isNotEmpty()) return digits
    val contact = contacts.firstOrNull { it.name == number }
    return contact?.number?.filter(Char::isDigit) ?: description.filter(Char::isDigit)
}

@Composable
private fun DetailHeader(
    displayName: String,
    secondaryNumber: String?,
    location: String,
    tag: String?,
    showVideo: Boolean,
    onDial: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.oppo_pb_ic_default_large_photo), "联系人头像", Modifier.size(86.dp))
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 13.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                displayName,
                style = oppoTextStyle(28.sp, weight = FontWeight.Medium),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryNumber != null) {
                Text(secondaryNumber, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, OppoSecondary))
            }
            if (tag != null) {
                Text(tag, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, TagOrange), textAlign = TextAlign.Center)
            }
            if (location.isNotBlank()) {
                Text(
                    location, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, OppoSecondary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailAction("拨号", R.drawable.oppo_pb_dr_detail_call_normal, Modifier.weight(1f), onClick = onDial)
            DetailAction("短信", R.drawable.oppo_pb_dr_detail_sms_normal, Modifier.weight(1f)) {}
            if (showVideo) {
                DetailAction("视频通话", R.drawable.oppo_pb_dr_video_call_disabled, Modifier.weight(1f), enabled = false) {}
            }
        }
    }
}

@Composable
private fun DetailAction(
    label: String,
    drawable: Int,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(drawable), null, Modifier.size(24.dp))
        Text(
            label, Modifier.padding(top = 4.dp),
            style = oppoTextStyle(12.sp, if (enabled) OppoPrimary else OppoTertiary),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryCard(
    records: List<CallRecord>,
    onOpenRecording: ((String) -> Unit)?,
    recordingFileFor: (String) -> File?,
    onViewRecordingFile: ((File) -> Unit)?,
) {
    var expandedRecording by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White),
    ) {
        if (records.isEmpty()) {
            Text("暂无通话记录", Modifier.padding(16.dp), style = oppoTextStyle(14.sp, OppoSecondary))
        } else {
            records.forEachIndexed { index, entry ->
                key(entry.storedId ?: index) {
                    CallHistoryItem(
                        record = entry,
                        onOpenRecording = onOpenRecording,
                        file = entry.storedId?.let(recordingFileFor),
                        onViewRecordingFile = onViewRecordingFile,
                        expanded = entry.storedId != null && expandedRecording == entry.storedId,
                        onExpand = { expandedRecording = entry.storedId },
                    )
                }
                if (index < records.lastIndex) OppoDivider(start = HistoryContentInset, end = HistoryEndInset)
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    record: CallRecord,
    onOpenRecording: ((String) -> Unit)?,
    file: File?,
    onViewRecordingFile: ((File) -> Unit)?,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    val id = record.storedId
    val audio = remember(file) { file?.takeIf(File::isFile)?.let(Uri::fromFile) }
    val hasPlayableRecording = record.hasRecording && audio != null
    val playback = if (expanded && hasPlayableRecording) rememberRecordingPlayback(checkNotNull(audio), autoPlay = true) else null
    Column(Modifier.animateContentSize(tween(150, easing = CubicBezierEasing(0.33f, 0f, 0.33f, 1f)))) {
        val action = if (id != null && onOpenRecording != null) Modifier.clickable(onClickLabel = "管理录音") { onOpenRecording(id) } else Modifier
        Row(
            Modifier.fillMaxWidth().then(action).heightIn(min = 72.dp)
                .padding(start = HistoryRowStartInset, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HistoryIconSpacing)) {
                Icon(
                    painterResource(record.kind.oppoIcon), null,
                    Modifier.padding(top = 3.dp).size(HistoryIconSize), tint = record.kind.indicatorColor,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        record.detailTime ?: record.date,
                        style = oppoTextStyle(17.sp, weight = FontWeight.Medium, includeFontPadding = false),
                    )
                    val label = record.kind.displayLabel()
                    val detail = record.durationDetail ?: if (record.isUnanswered) "$label  未接通" else label
                    Text(detail, style = oppoTextStyle(14.sp, OppoSecondary, includeFontPadding = false))
                }
            }
            if (hasPlayableRecording && id != null) {
                val playing = playback?.playing == true
                IconButton(
                    enabled = !expanded || playback?.ready == true,
                    onClick = { if (!expanded) onExpand() else playback?.toggle() },
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "暂停录音" else "播放录音",
                        Modifier.size(36.dp).background(PlaybackButtonBackground, CircleShape).padding(8.dp),
                        tint = PlaybackButtonIcon,
                    )
                }
            }
        }
        if (playback != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                if (playback.ready) {
                    RecordingProgress(playback.position, playback.duration, playback::seekTo)
                } else {
                    Text(
                        if (playback.failed) "录音文件无法播放" else "正在加载录音…",
                        Modifier.padding(start = HistoryContentInset, end = HistoryEndInset, top = 6.dp),
                        style = oppoTextStyle(14.sp, if (playback.failed) MissedRed else OppoSecondary),
                    )
                }
                if ((file != null && onViewRecordingFile != null) || (id != null && onOpenRecording != null)) {
                    TextButton(
                        onClick = {
                            if (file != null && onViewRecordingFile != null) onViewRecordingFile(file)
                            else if (id != null) onOpenRecording?.invoke(id)
                        },
                        modifier = Modifier.align(Alignment.End).padding(end = HistoryEndInset - HistoryActionPadding),
                        colors = ButtonDefaults.textButtonColors(contentColor = DetailAccent),
                        contentPadding = HistoryActionContentPadding,
                    ) { Text("查看文件", style = HistoryActionTextStyle) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingProgress(position: Int, duration: Int, onSeek: (Int) -> Unit) {
    var seekingTo by remember { mutableStateOf<Int?>(null) }
    val maximum = duration.coerceAtLeast(1)
    val displayedPosition = (seekingTo ?: position).coerceIn(0, maximum)
    val colors = SliderDefaults.colors(activeTrackColor = OppoGreen, inactiveTrackColor = Color(0xFFDEDEDE))
    // Material insets the visible track by half the thumb width at each end.
    val thumbRadius = RecordingThumbSize / 2
    Slider(
        value = displayedPosition.toFloat(),
        onValueChange = { seekingTo = it.roundToInt() },
        onValueChangeFinished = {
            seekingTo?.let(onSeek)
            seekingTo = null
        },
        modifier = Modifier.fillMaxWidth()
            .padding(start = HistoryContentInset - thumbRadius, end = HistoryEndInset - thumbRadius)
            .semantics { contentDescription = "录音播放进度" },
        enabled = duration > 0,
        valueRange = 0f..maximum.toFloat(),
        thumb = {
            Box(Modifier.size(RecordingThumbSize).background(Color.White, CircleShape).border(2.dp, OppoGreen, CircleShape))
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(12.dp),
                colors = colors,
                enabled = duration > 0,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
    val timeStyle = oppoTextStyle(13.sp, OppoSecondary, FontWeight.Medium, includeFontPadding = false)
        .copy(fontFeatureSettings = "tnum")
    Row(
        Modifier.fillMaxWidth().padding(start = HistoryContentInset, end = HistoryEndInset),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatRecordingTime(displayedPosition.toLong()), style = timeStyle)
        Text(formatRecordingTime(duration.toLong()), style = timeStyle)
    }
}

@Preview(name = "通话详情", showBackground = true, widthDp = 360, heightDp = 784)
@Composable
private fun CallDetailPreview() {
    val sample = CallRecord("13800138000", "北京 移动", "2026/10/21", CallKind.OUTGOING,
        detailTime = "2026/10/21 14:30", durationDetail = "呼出  未接通", isUnanswered = true, demoIndex = 0)
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        CallDetailScreen(
            record = sample,
            allRecords = listOf(
                sample,
                sample.copy(kind = CallKind.INCOMING, detailTime = "2026/10/20 10:15", durationDetail = "呼入  53秒", isUnanswered = false),
                sample.copy(kind = CallKind.MISSED, detailTime = "2026/10/19 11:33", durationDetail = null),
            ),
            onBack = {},
            onDial = {},
        )
    }
}

@Preview(name = "联系人通话详情", showBackground = true, widthDp = 360, heightDp = 784)
@Composable
private fun ContactCallDetailPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        CallDetailScreen(
            record = CallRecord("10086", "未知归属地", "2026/10/21", CallKind.OUTGOING),
            allRecords = emptyList(),
            contacts = listOf(ContactMatch("中国移动", "10086", "北京 移动")),
            fromContact = true,
            onBack = {},
            onDial = {},
        )
    }
}
