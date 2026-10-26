package com.lastcoffee.top.oppo_call_compose.feature.call

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.model.displayLabel
import com.lastcoffee.top.oppo_call_compose.ui.common.MissedRed
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoCardPage
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDivider
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDividerHeight
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoGreen
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoPrimary
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoSecondary
import com.lastcoffee.top.oppo_call_compose.ui.common.TagOrange
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoIcon
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoTextStyle
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.launch

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
) {
    val number = record.phoneNumber(contacts)
    val contact = remember(record, contacts) {
        contacts.firstOrNull { it.number.filter(Char::isDigit) == number || it.name == record.number }
    }
    val isEmergency = record.number == "紧急呼叫中心"
    val displayName = if (isEmergency) record.number else contact?.name ?: record.number
    val location = customLocation ?: contact?.location ?: when {
        isEmergency -> number
        record.storedId != null -> "未知归属地"
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
            // appbar_toolbar_layout.xml: COUICollapsedToolbarStyle is 52dp high.
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                IconButton(onClick = { if (showAllHistory) showAllHistory = false else onBack() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Image(painterResource(R.drawable.oppo_coui_back_arrow_normal), "返回", Modifier.size(24.dp))
                }
                if (showAllHistory) {
                    Text("通话记录", Modifier.align(Alignment.Center), style = oppoTextStyle(18.sp, weight = FontWeight.Medium))
                } else {
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                        if (contact == null && !fromContact) {
                            IconButton(onClick = {}) {
                                Image(painterResource(R.drawable.oppo_pb_dr_menu_add_normal), "添加联系人", Modifier.size(24.dp))
                            }
                        }
                        if (!fromContact && (record.storedId != null || record.demoIndex != null)) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Image(painterResource(R.drawable.oppo_coui_toolbar_menu_icon_more_normal), "更多", Modifier.size(24.dp))
                                }
                                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("编辑号码") }, onClick = { menuExpanded = false; onEditNumber(number) })
                                    DropdownMenuItem(text = { Text("删除通话记录") }, onClick = { menuExpanded = false; showDeleteDialog = true })
                                }
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (!showAllHistory) {
                    item {
                        // call_detail_unfamiliar_number.xml: 86dp photo, 13dp title gap,
                        // HeadlineL=28sp, BodyM=14sp, and 24dp below the number block.
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.oppo_pb_ic_default_large_photo), "联系人头像", Modifier.size(86.dp))
                            Column(
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 13.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(displayName, style = oppoTextStyle(28.sp, weight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (contact != null && !isEmergency) {
                                    Text(number, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, OppoSecondary))
                                }
                                if (record.tag != null && !isEmergency) {
                                    Text(record.tag, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, TagOrange))
                                }
                                Text(location, Modifier.padding(top = 4.67.dp), style = oppoTextStyle(14.sp, OppoSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        // detail_header_bottom_layout.xml: outer 12dp + each card's 4dp.
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
                            DetailAction("拨号", R.drawable.oppo_pb_dr_detail_call_normal, Modifier.weight(1f)) { onDial(number) }
                            DetailAction("短信", R.drawable.oppo_pb_dr_detail_sms_normal, Modifier.weight(1f)) {}
                            if (!isEmergency) {
                                DetailAction("视频通话", R.drawable.oppo_pb_dr_video_call_disabled, Modifier.weight(1f), enabled = false) {}
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("通话记录", Modifier.weight(1f).padding(start = 16.dp), style = oppoTextStyle(12.sp, OppoSecondary))
                            if (history.isNotEmpty()) {
                                Text(
                                    "查看全部", Modifier.padding(end = 4.dp).clickable { showAllHistory = true }.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = oppoTextStyle(12.sp, OppoGreen, FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
                item { HistoryCard(if (showAllHistory) history else history.take(3)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
            if (!showAllHistory && !isEmergency && contact == null) {
                val provider = buildAnnotatedString {
                    append("该号码识别结果由")
                    withStyle(SpanStyle(color = OppoGreen)) { append("泰迪未来科技") }
                    append("提供")
                }
                Text(
                    provider, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    style = oppoTextStyle(12.sp, OppoSecondary), textAlign = TextAlign.Center,
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
private fun DetailAction(label: String, drawable: Int, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(drawable), label, Modifier.size(24.dp))
        Text(label, Modifier.padding(top = 4.dp), style = oppoTextStyle(12.sp, if (enabled) OppoPrimary else Color(0x42000000)), maxLines = 1)
    }
}

@Composable
private fun HistoryCard(records: List<CallRecord>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White).padding(vertical = 2.dp),
    ) {
        if (records.isEmpty()) {
            Text("暂无通话记录", Modifier.padding(16.dp), style = oppoTextStyle(14.sp, OppoSecondary))
        } else {
            records.forEachIndexed { index, entry ->
                CallHistoryItem(entry)
                // detail_call_history_item.xml uses screen-relative 52dp / 32dp.
                // Subtract the card's 16dp inset, instead of adding another inset.
                if (index < records.lastIndex) OppoDivider(start = 36.dp, end = 16.dp)
                else Spacer(Modifier.height(OppoDividerHeight))
            }
        }
    }
}

@Composable
private fun CallHistoryItem(record: CallRecord) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(record.kind.oppoIcon), record.kind.displayLabel(),
            Modifier.padding(top = 3.dp).size(16.dp), tint = if (record.isUnanswered) MissedRed else Color.Unspecified)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(record.detailTime ?: record.date, style = oppoTextStyle(16.sp, weight = FontWeight.Medium), maxLines = 1)
            val detail = if (record.isUnanswered) "${record.kind.displayLabel()}  未接通" else record.durationDetail ?: record.kind.displayLabel()
            Text(detail, Modifier.padding(top = 5.dp), style = oppoTextStyle(14.sp, OppoSecondary), maxLines = 1)
        }
    }
}

@Preview(name = "OPPO 通话详情 · 未接通", showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun CallDetailPreview() {
    val sample = CallRecord("10086", "未知归属地", "2026/10/21", CallKind.OUTGOING,
        detailTime = "2026/10/21 14:30", durationDetail = "呼出  未接通", isUnanswered = true)
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        CallDetailScreen(
            record = sample,
            allRecords = listOf(sample, sample.copy(detailTime = "2026/10/20 10:15")),
            onBack = {},
            onDial = {},
        )
    }
}

@Preview(name = "OPPO 通话详情 · 联系人", showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun ContactCallDetailPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        CallDetailScreen(
            record = CallRecord("10086", "未知归属地", "2026/10/21", CallKind.OUTGOING),
            allRecords = emptyList(),
            onBack = {},
            onDial = {},
        )
    }
}
