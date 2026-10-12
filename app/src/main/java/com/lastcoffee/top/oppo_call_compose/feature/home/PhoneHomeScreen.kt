package com.lastcoffee.top.oppo_call_compose.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
import com.lastcoffee.top.oppo_call_compose.feature.contacts.ContactsScreen
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.ui.common.DesignSegmentWidth
import com.lastcoffee.top.oppo_call_compose.ui.common.DividerColor
import com.lastcoffee.top.oppo_call_compose.ui.common.MissedRed
import com.lastcoffee.top.oppo_call_compose.ui.common.NavigationSelected
import com.lastcoffee.top.oppo_call_compose.ui.common.NavigationUnselected
import com.lastcoffee.top.oppo_call_compose.ui.common.SecondaryText
import com.lastcoffee.top.oppo_call_compose.ui.common.TagOrange
import kotlinx.coroutines.launch

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    CALLS("通话", Icons.Filled.Phone),
    CONTACTS("联系人", Icons.Filled.Contacts),
    STORE("营业厅", Icons.Filled.Storefront),
}

@Composable
internal fun PhoneHomeScreen(
    records: List<CallRecord>,
    onShowDetail: (CallRecord) -> Unit,
    onShowContact: (ContactMatch) -> Unit,
    onStartCall: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    pendingDialNumber: String?,
    onDialRequestConsumed: () -> Unit,
    onClearCalls: suspend () -> Boolean,
    onDial: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(PhoneTab.CALLS) }
    var dialPadVisible by remember { mutableStateOf(false) }
    var dialedNumber by remember { mutableStateOf("") }
    LaunchedEffect(pendingDialNumber) {
        if (pendingDialNumber != null) {
            selectedTab = PhoneTab.CALLS
            dialedNumber = pendingDialNumber
            dialPadVisible = true
            onDialRequestConsumed()
        }
    }
    BackHandler(enabled = dialPadVisible) {
        dialPadVisible = false
        dialedNumber = ""
    }

    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    PhoneTab.CALLS -> CallsScreen(
                        records = records,
                        dialPadVisible = dialPadVisible,
                        dialedNumber = dialedNumber,
                        onShowDialPad = { dialPadVisible = true },
                        onHideDialPad = {
                            dialPadVisible = false
                            dialedNumber = ""
                        },
                        onDialNumberChange = { dialedNumber = it },
                        onDial = { number ->
                            if (number.isNotBlank()) onStartCall(number) else onDial(number)
                        },
                        onShowDetail = onShowDetail,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                        onClearCalls = onClearCalls,
                    )
                    PhoneTab.CONTACTS -> ContactsScreen(onShowContact = onShowContact)
                    PhoneTab.STORE -> SimpleDestination("营业厅", "套餐与便民服务将在这里显示", Icons.Filled.Storefront)
                }
            }
            BottomNavigationBar(selectedTab = selectedTab, onSelected = { tab ->
                selectedTab = tab
                if (tab != PhoneTab.CALLS) dialPadVisible = false
            })
        }
    }
}

@Composable
private fun CallsScreen(
    records: List<CallRecord>,
    dialPadVisible: Boolean,
    dialedNumber: String,
    onShowDialPad: () -> Unit,
    onHideDialPad: () -> Unit,
    onDialNumberChange: (String) -> Unit,
    onDial: (String) -> Unit,
    onShowDetail: (CallRecord) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearCalls: suspend () -> Boolean,
) {
    var missedOnly by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val headerTravelPx = with(LocalDensity.current) { 56.dp.toPx() }
    var headerScrollPx by remember { mutableFloatStateOf(0f) }
    val headerScroll = remember(listState, headerTravelPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
                    return Offset.Zero
                }
                val next = (headerScrollPx - delta).coerceIn(0f, headerTravelPx)
                val consumed = headerScrollPx - next
                headerScrollPx = next
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (headerScrollPx - available.y).coerceAtLeast(0f)
                val used = headerScrollPx - next
                headerScrollPx = next
                return Offset(0f, used)
            }
        }
    }
    val headerProgress = (headerScrollPx / headerTravelPx).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().then(if (dialedNumber.isBlank()) Modifier.nestedScroll(headerScroll) else Modifier)) {
            // Opening the dialpad alone keeps the call-page tabs visible; typing a
            // digit switches to the source-style number-search surface.
            if (!dialPadVisible || dialedNumber.isBlank()) {
                CallsTopBar(
                    collapseProgress = headerProgress,
                    missedOnly = missedOnly,
                    onFilterChange = { missedOnly = it },
                    onMenuClick = { menuVisible = true },
                    menuVisible = menuVisible,
                    onDismissMenu = { menuVisible = false },
                    onSettingsClick = onOpenSettings,
                    onSearchClick = onOpenSearch,
                    onClearClick = {
                        menuVisible = false
                        showClearDialog = true
                    },
                )
            }
            if (dialedNumber.isNotBlank()) {
                DialSearchResults(number = dialedNumber, onInfo = onShowDetail)
            } else {
                CallList(
                    records = if (missedOnly) records.filter { it.kind == CallKind.MISSED } else records,
                    state = listState,
                    onInfo = onShowDetail,
                    contentBottomPadding = if (dialPadVisible) 370.dp else 72.dp,
                )
            }
        }

        if (!dialPadVisible) {
            DialPadFab(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp),
                onClick = onShowDialPad,
            )
        }

        AnimatedVisibility(
            visible = dialPadVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DialPad(
                number = dialedNumber,
                onNumberChange = onDialNumberChange,
                onDial = { onDial(dialedNumber.filter(Char::isDigit)) },
                onDismiss = onHideDialPad,
            )
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { if (!clearing) showClearDialog = false },
            title = { Text("要删除全部通话记录吗？") },
            text = { Text(if (clearFailed) "删除失败，请重试" else "全部通话记录将从本设备彻底删除") },
            confirmButton = {
                TextButton(onClick = {
                    clearing = true
                    scope.launch {
                        if (onClearCalls()) {
                            showClearDialog = false
                            clearFailed = false
                        } else {
                            clearFailed = true
                        }
                        clearing = false
                    }
                }, enabled = !clearing) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }, enabled = !clearing) { Text("取消") } },
        )
    }
}

@Composable
private fun CallsTopBar(
    collapseProgress: Float,
    missedOnly: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    menuVisible: Boolean,
    onDismissMenu: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp - 56.dp * collapseProgress)
            .background(Color.White),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp)) {
            SegmentedFilter(
                missedOnly,
                onFilterChange,
                modifier = Modifier.requiredWidth(DesignSegmentWidth).align(Alignment.Center),
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(24.dp))
                }
                Box {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(text = { Text("设置") }, onClick = {
                            onDismissMenu()
                            onSettingsClick()
                        })
                        DropdownMenuItem(text = { Text("清除通话记录") }, onClick = onClearClick)
                    }
                }
            }
        }
        Box(
            modifier = Modifier.offset(x = 16.dp, y = 56.dp * (1f - collapseProgress)).height(56.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("通话", fontSize = (30f - 10f * collapseProgress).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        HorizontalDivider(
            color = DividerColor.copy(alpha = collapseProgress),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SegmentedFilter(missedOnly: Boolean, onFilterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9E9E9)).padding(2.dp),
    ) {
        FilterChoice("全部", selected = !missedOnly, modifier = Modifier.weight(1f)) { onFilterChange(false) }
        FilterChoice("未接", selected = missedOnly, modifier = Modifier.weight(1f)) { onFilterChange(true) }
    }
}

@Composable
private fun FilterChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = if (selected) Color(0xFF242424) else SecondaryText)
    }
}

@Composable
private fun CallList(
    records: List<CallRecord>,
    state: androidx.compose.foundation.lazy.LazyListState,
    onInfo: (CallRecord) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        items(records) { record ->
            CallRow(record = record, onInfo = { onInfo(record) })
        }
        item { Spacer(modifier = Modifier.height(contentBottomPadding)) }
    }
}

@Composable
private fun CallRow(record: CallRecord, onInfo: () -> Unit) {
    val primaryColor = if (record.kind == CallKind.MISSED) MissedRed else Color(0xFF171717)
    Row(modifier = Modifier.fillMaxWidth().padding(start = 21.dp), verticalAlignment = Alignment.Top) {
        Icon(
            record.kind.icon,
            contentDescription = null,
            tint = if (record.kind == CallKind.MISSED) MissedRed else SecondaryText,
            modifier = Modifier.padding(top = 14.dp).size(16.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, end = 2.dp, bottom = 8.dp)) {
            Text(record.number, color = primaryColor, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            Text(record.description, color = SecondaryText, fontSize = 14.sp, lineHeight = 20.sp)
            record.tag?.let { Text(it, color = TagOrange, fontSize = 14.sp, lineHeight = 18.sp) }
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(top = 9.dp))
        }
        Text(record.date, color = SecondaryText, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        IconButton(onClick = onInfo, modifier = Modifier.padding(top = 6.dp, end = 4.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "详情", tint = Color(0xFF151515), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DialSearchResults(number: String, onInfo: (CallRecord) -> Unit) {
    val matches = remember(number) { demoContacts.filter { it.number.contains(number) }.ifEmpty { demoContacts } }
    Column(modifier = Modifier.fillMaxSize()) {
        // dialpad_layout.xml positions paste_and_digit_container 70dp below the dial content top.
        Spacer(modifier = Modifier.height(70.dp))
        // Source digits_container.xml is a 56.33dp header: a centered 32dp number
        // with the number-recognition/location line directly beneath it.
        Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                number,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 1.dp, start = 24.dp, end = 24.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                "中国大陆",
                color = SecondaryText,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(matches) { contact ->
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(contact.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                        HighlightedNumber(contact.number, number)
                    }
                    Text(contact.location, color = SecondaryText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                    IconButton(onClick = { onInfo(CallRecord(contact.number, contact.location, "联系人", CallKind.INCOMING)) }) {
                        Icon(Icons.Filled.Info, contentDescription = "详情", modifier = Modifier.size(24.dp))
                    }
                }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 16.dp))
            }
            item { Spacer(modifier = Modifier.height(354.dp)) }
        }
    }
}

@Composable
private fun HighlightedNumber(number: String, query: String) {
    val start = number.indexOf(query)
    val text = buildAnnotatedString {
        if (start < 0) append(number) else {
            append(number.take(start))
            withStyle(SpanStyle(color = Color(0xFF36962B))) { append(number.substring(start, start + query.length)) }
            append(number.drop(start + query.length))
        }
    }
    Text(text, color = SecondaryText, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun DialPadFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.size(56.dp).shadow(6.dp, RoundedCornerShape(56.dp)),
        shape = RoundedCornerShape(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BD13)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(Icons.Filled.Dialpad, contentDescription = "打开拨号盘", modifier = Modifier.size(24.dp), tint = Color.White)
    }
}

private data class DialKey(val digit: String, val letters: String, val voicemailMark: Boolean = false)

private val dialKeys = listOf(
    // DialpadView uses PINYIN_MODE: the digit grid stays numeric and the
    // Latin T9 labels are shown as the secondary text on each key.
    DialKey("1", "", voicemailMark = true), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*", ","), DialKey("0", "+"), DialKey("#", ";"),
)

@Composable
private fun DialPad(number: String, onNumberChange: (String) -> Unit, onDial: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(346.dp).shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFFFEFEFE),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Color(0xFFC5C5C5)))
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                dialKeys.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        row.forEach { key ->
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .clickable(
                                        interactionSource = null,
                                        indication = ripple(bounded = true, radius = 29.dp),
                                        onClickLabel = "输入${key.digit}",
                                        onClick = { onNumberChange(number + key.digit) },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(key.digit, fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 34.sp)
                                if (key.voicemailMark) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_dial_key_voicemail),
                                        contentDescription = "语音信箱",
                                        modifier = Modifier.padding(top = 4.dp).size(width = 14.dp, height = 8.dp),
                                    )
                                } else {
                                    Text(
                                        key.letters,
                                        color = Color(0x8A000000),
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Dialpad, contentDescription = "收起拨号盘", modifier = Modifier.size(31.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onDial,
                        modifier = Modifier.size(width = 106.dp, height = 40.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BD13)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Icon(Icons.Filled.Call, contentDescription = "拨打", modifier = Modifier.size(24.dp)) }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { if (number.isNotEmpty()) onNumberChange(number.dropLast(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除", tint = if (number.isEmpty()) Color(0xFFC5C5C5) else Color(0xFF151515), modifier = Modifier.size(31.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(selectedTab: PhoneTab, onSelected: (PhoneTab) -> Unit) {
    // ContactsTabActivity uses contact_bottom_tab_height=56dp, 24dp icons and 10sp labels.
    Row(modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
        PhoneTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelected(tab) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) NavigationSelected else NavigationUnselected,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(tab.label, fontSize = 10.sp, color = if (selected) NavigationSelected else NavigationUnselected)
            }
        }
    }
}

@Composable
private fun SimpleDestination(title: String, subtitle: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 42.dp))
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFFB7B7B7), modifier = Modifier.size(64.dp))
            Text(subtitle, color = SecondaryText, fontSize = 20.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
