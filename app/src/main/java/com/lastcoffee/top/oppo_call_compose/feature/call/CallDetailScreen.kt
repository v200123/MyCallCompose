package com.lastcoffee.top.oppo_call_compose.feature.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.data.demoContacts
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.ui.common.DividerColor
import com.lastcoffee.top.oppo_call_compose.ui.common.MissedRed
import com.lastcoffee.top.oppo_call_compose.ui.common.SecondaryText
import com.lastcoffee.top.oppo_call_compose.ui.common.TagOrange
import kotlinx.coroutines.launch

@Composable
internal fun CallDetailScreen(
    record: CallRecord,
    allRecords: List<CallRecord>,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onEditNumber: (String) -> Unit = {},
    onDelete: suspend (CallRecord) -> Boolean = { false },
    fromContact: Boolean = false,
) {
    val isEmergency = record.number == "紧急呼叫中心"
    val contact = remember(record) { demoContacts.firstOrNull { it.number == record.number } }
    val displayName = if (isEmergency) "紧急呼叫中心" else contact?.name ?: record.number
    val number = if (isEmergency) record.description else contact?.number
        ?: record.description.takeIf { it.all(Char::isDigit) } ?: record.number
    val normalizedDescription = record.description.replace("\n", "  ")
    val location = contact?.location ?: when {
        isEmergency -> ""
        record.storedId != null -> "未知归属地"
        record.description.contains("\n") -> record.description.substringAfter("\n").trim()
        else -> normalizedDescription.removePrefix("小布通话").trim()
    }
    val records = remember(record, allRecords) {
        if (isEmergency) listOf(
            CallHistoryEntry("2026/9/27 21:58", CallKind.OUTGOING, "未接通"),
            CallHistoryEntry("2026/9/17 16:30", CallKind.OUTGOING, "未接通"),
            CallHistoryEntry("2026/9/17 16:27", CallKind.OUTGOING, "未接通"),
        ) else allRecords.filter { historyRecord ->
            historyRecord.number == record.number ||
                (contact != null && (historyRecord.number == contact.name || historyRecord.description == contact.number))
        }.map { historyRecord ->
            CallHistoryEntry(
                date = historyRecord.detailTime ?: "${historyRecord.date} 17:22",
                kind = historyRecord.kind,
                detail = historyRecord.durationDetail ?: when (historyRecord.kind) {
                    CallKind.INCOMING -> "呼入  2分2秒"
                    CallKind.OUTGOING -> "呼出  35 秒"
                    CallKind.MISSED -> "呼出  未接通"
                    CallKind.BLOCKED -> "拒接 未接通"
                },
            )
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAllHistory by remember(record) { mutableStateOf(false) }
    var showDeleteDialog by remember(record) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(28.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (contact == null && !fromContact) IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                        Image(painterResource(R.drawable.ic_detail_add_contact), contentDescription = "添加联系人", modifier = Modifier.size(28.dp))
                    }
                    if (!fromContact && (record.storedId != null || record.demoIndex != null)) Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(28.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("编辑号码") }, onClick = {
                                menuExpanded = false
                                onEditNumber(number.filter { it.isDigit() || it in "+*#" })
                            })
                            DropdownMenuItem(text = { Text("删除通话记录") }, onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            })
                        }
                    }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(86.dp).clip(CircleShape).background(Color(0xFFCCCDCF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(R.drawable.ic_detail_default_avatar), contentDescription = "联系人头像", modifier = Modifier.fillMaxSize())
                        }
                        Text(
                            displayName,
                            fontSize = if (isEmergency) 32.sp else 36.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF171719),
                            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp),
                            maxLines = 1,
                        )
                        if (!isEmergency && record.tag != null) {
                            Text("${record.tag} | 67 人标记", fontSize = 19.sp, color = TagOrange, modifier = Modifier.padding(top = 12.dp), maxLines = 1)
                        }
                        val subline = if (isEmergency) number else location.ifBlank { number }
                        Text(
                            subline,
                            fontSize = 20.sp,
                            color = SecondaryText,
                            modifier = Modifier.padding(top = if (record.tag != null && !isEmergency) 1.dp else 8.dp),
                            maxLines = 1,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DetailAction("拨号", Icons.Filled.Call, Modifier.weight(1f)) { onDial(number.filter(Char::isDigit)) }
                        DetailAction("短信", Icons.Filled.Sms, Modifier.weight(1f)) { }
                        if (!isEmergency) DetailAction("视频通话", Icons.Filled.VideoCall, Modifier.weight(1f), enabled = false) { }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 40.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("通话记录", fontSize = 20.sp, color = SecondaryText)
                        if (records.size > 3) {
                            Text(
                                if (showAllHistory) "收起" else "查看全部",
                                fontSize = 18.sp,
                                color = Color(0xFF249642),
                                modifier = Modifier.clickable { showAllHistory = !showAllHistory },
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(18.dp)).background(Color.White),
                    ) {
                        if (records.isEmpty()) {
                            Text("暂无通话记录", color = SecondaryText, fontSize = 16.sp, modifier = Modifier.padding(20.dp))
                        } else {
                            val visibleRecords = if (showAllHistory) records else records.take(3)
                            visibleRecords.forEachIndexed { index, entry ->
                                CallHistoryItem(entry)
                                if (index < visibleRecords.lastIndex) {
                                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 48.dp, end = 22.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(22.dp)) }
            }
            if (!isEmergency && contact == null) {
                val annotatedProvider = buildAnnotatedString {
                    withStyle(SpanStyle(color = SecondaryText)) { append("该号码识别结果由") }
                    withStyle(SpanStyle(color = Color(0xFF249642))) { append("泰迪未来科技") }
                    withStyle(SpanStyle(color = SecondaryText)) { append("提供") }
                }
                Text(
                    annotatedProvider,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("要删除此通话记录吗？") },
            text = { Text(if (deleteFailed) "删除失败，请重试" else "此通话记录将从本设备彻底删除") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = true
                    scope.launch {
                        deleteFailed = !onDelete(record)
                        deleting = false
                    }
                }, enabled = !deleting) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }, enabled = !deleting) { Text("取消") } },
        )
    }
}

@Composable
private fun DetailAction(label: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = modifier.height(72.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color(0xFF171719) else Color(0xFFBDBDBD), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 16.sp, color = if (enabled) Color(0xFF171719) else Color(0xFFBDBDBD), modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

private data class CallHistoryEntry(val date: String, val kind: CallKind, val detail: String)

@Composable
private fun CallHistoryItem(entry: CallHistoryEntry) {
    val typeLabel = when (entry.kind) {
        CallKind.INCOMING -> "呼入"
        CallKind.OUTGOING -> "呼出"
        CallKind.MISSED -> "未接来电"
        CallKind.BLOCKED -> "拒接"
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            entry.kind.icon,
            contentDescription = typeLabel,
            tint = if (entry.kind == CallKind.MISSED || entry.kind == CallKind.BLOCKED) MissedRed else SecondaryText,
            modifier = Modifier.padding(top = 5.dp).size(16.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.date, fontSize = 23.sp, color = Color(0xFF171719), maxLines = 1)
            Text("$typeLabel  ${entry.detail.substringAfter(' ')}", fontSize = 20.sp, color = SecondaryText, modifier = Modifier.padding(top = 7.dp), maxLines = 1)
        }
    }
}
