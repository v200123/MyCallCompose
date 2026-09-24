package com.lastcoffee.top.oppo_call_compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

private val OppoGreen = Color(0xFF49BE31)
private val MissedRed = Color(0xFFD4483A)
private val SecondaryText = Color(0xFF858585)
private val DividerColor = Color(0xFFEEEEEE)
private val TagOrange = Color(0xFFFF7926)

// The supplied design is 1080 px wide at 2x density, so its Compose baseline is 540 dp.
private val DesignSegmentWidth = 196.dp

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    CALLS("通话", Icons.Filled.Phone),
    CONTACTS("联系人", Icons.Filled.Contacts),
    STORE("营业厅", Icons.Filled.Storefront),
}

@Composable
fun OppoPhoneApp(onDial: (String) -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(PhoneTab.CALLS) }
    var dialPadVisible by remember { mutableStateOf(false) }
    var dialedNumber by remember { mutableStateOf("") }

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
                        dialPadVisible = dialPadVisible,
                        dialedNumber = dialedNumber,
                        onShowDialPad = { dialPadVisible = true },
                        onHideDialPad = {
                            dialPadVisible = false
                            dialedNumber = ""
                        },
                        onDialNumberChange = { dialedNumber = it },
                        onDial = onDial,
                    )
                    PhoneTab.CONTACTS -> SimpleDestination("联系人", "联系人列表将在这里显示", Icons.Filled.Contacts)
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
    dialPadVisible: Boolean,
    dialedNumber: String,
    onShowDialPad: () -> Unit,
    onHideDialPad: () -> Unit,
    onDialNumberChange: (String) -> Unit,
    onDial: (String) -> Unit,
) {
    var missedOnly by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<CallRecord?>(null) }
    val listState = rememberLazyListState()
    var compactHeader by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 130 }
            .collect { compactHeader = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CallsTopBar(
                compact = compactHeader,
                missedOnly = missedOnly,
                onFilterChange = { missedOnly = it },
                onMenuClick = { menuVisible = true },
                menuVisible = menuVisible,
                onDismissMenu = { menuVisible = false },
                onSearchClick = onShowDialPad,
            )
            if (dialedNumber.isNotBlank()) {
                DialSearchResults(number = dialedNumber, onInfo = { selectedRecord = it })
            } else {
                CallList(
                    records = if (missedOnly) demoCalls.filter { it.kind == CallKind.MISSED } else demoCalls,
                    state = listState,
                    onInfo = { selectedRecord = it },
                    contentBottomPadding = if (dialPadVisible) 570.dp else 128.dp,
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

    selectedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecord = null },
            title = { Text(record.number) },
            text = { Text("${record.description}\n${record.date}") },
            confirmButton = {
                TextButton(onClick = { selectedRecord = null; onDial(record.number.filter(Char::isDigit)) }) {
                    Text("拨打", color = OppoGreen)
                }
            },
            dismissButton = { TextButton(onClick = { selectedRecord = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun CallsTopBar(
    compact: Boolean,
    missedOnly: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    menuVisible: Boolean,
    onDismissMenu: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val topPadding by animateDpAsState(if (compact) 8.dp else 18.dp, label = "top padding")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 14.dp, top = topPadding, bottom = 10.dp)
            .height(50.dp),
    ) {
        if (compact) {
            Text(
                "通话",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        SegmentedFilter(
            missedOnly,
            onFilterChange,
            modifier = Modifier.width(DesignSegmentWidth).align(Alignment.Center),
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(34.dp))
            }
            Box {
                IconButton(onClick = onMenuClick) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = menuVisible, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("通话设置") }, onClick = onDismissMenu)
                    DropdownMenuItem(text = { Text("清除通话记录") }, onClick = onDismissMenu)
                }
            }
        }
    }
    if (compact) HorizontalDivider(color = DividerColor)
}

@Composable
private fun SegmentedFilter(missedOnly: Boolean, onFilterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFFE9E9E9)).padding(3.dp),
    ) {
        FilterChoice("全部", selected = !missedOnly, modifier = Modifier.weight(1f)) { onFilterChange(false) }
        FilterChoice("未接", selected = missedOnly, modifier = Modifier.weight(1f)) { onFilterChange(true) }
    }
}

@Composable
private fun FilterChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, color = if (selected) Color(0xFF242424) else SecondaryText)
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
        item {
            Text(
                "通话",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 24.dp),
            )
        }
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
            modifier = Modifier.padding(top = 21.dp).size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, top = 14.dp, end = 2.dp, bottom = 12.dp)) {
            Text(record.number, color = primaryColor, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
            Text(record.description, color = SecondaryText, fontSize = 24.sp, lineHeight = 32.sp)
            record.tag?.let { Text(it, color = TagOrange, fontSize = 24.sp, lineHeight = 30.sp) }
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(top = 13.dp))
        }
        Text(record.date, color = SecondaryText, fontSize = 24.sp, modifier = Modifier.padding(top = 24.dp))
        IconButton(onClick = onInfo, modifier = Modifier.padding(top = 12.dp, end = 10.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "详情", tint = Color(0xFF151515), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun DialSearchResults(number: String, onInfo: (CallRecord) -> Unit) {
    val matches = remember(number) { demoContacts.filter { it.number.contains(number) }.ifEmpty { demoContacts } }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(number, fontSize = 44.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), textAlign = TextAlign.Center)
        LazyColumn {
            items(matches) { contact ->
                Row(modifier = Modifier.fillMaxWidth().padding(start = 21.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 13.dp)) {
                        Text(contact.name, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        HighlightedNumber(contact.number, number)
                    }
                    Text(contact.location, color = SecondaryText, fontSize = 22.sp, lineHeight = 29.sp, modifier = Modifier.width(138.dp), textAlign = TextAlign.End)
                    IconButton(onClick = { onInfo(CallRecord(contact.number, contact.location, "联系人", CallKind.INCOMING)) }) {
                        Icon(Icons.Filled.Info, contentDescription = "详情", modifier = Modifier.size(28.dp))
                    }
                }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 21.dp))
            }
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
    Text(text, color = SecondaryText, fontSize = 24.sp)
}

@Composable
private fun DialPadFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.size(88.dp).shadow(12.dp, CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = OppoGreen),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(Icons.Filled.Dialpad, contentDescription = "打开拨号盘", modifier = Modifier.size(40.dp), tint = Color.White)
    }
}

private data class DialKey(val digit: String, val letters: String)

private val dialKeys = listOf(
    DialKey("1", "◉"), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*", "，"), DialKey("0", "+"), DialKey("#", "；"),
)

@Composable
private fun DialPad(number: String, onNumberChange: (String) -> Unit, onDial: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(550.dp).shadow(18.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFFFEFEFE),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(top = 15.dp, bottom = 12.dp).size(width = 48.dp, height = 5.dp).clip(CircleShape).background(Color(0xFFC5C5C5)))
            dialKeys.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().clickable { onNumberChange(number + key.digit) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(key.digit, fontSize = 42.sp, fontWeight = FontWeight.Medium, lineHeight = 43.sp)
                            Text(key.letters, fontSize = 16.sp, color = SecondaryText, lineHeight = 17.sp)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(108.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Dialpad, contentDescription = "收起拨号盘", modifier = Modifier.size(31.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onDial,
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = OppoGreen),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Icon(Icons.Filled.Call, contentDescription = "拨打", modifier = Modifier.size(40.dp)) }
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
    Row(modifier = Modifier.fillMaxWidth().height(96.dp).background(Color(0xFFFCFCFC)), verticalAlignment = Alignment.CenterVertically) {
        PhoneTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelected(tab) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(34.dp), tint = if (selected) Color(0xFF171717) else SecondaryText)
                Spacer(modifier = Modifier.height(4.dp))
                Text(tab.label, fontSize = 16.sp, color = if (selected) Color(0xFF171717) else SecondaryText)
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

@Preview(showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun OppoPhonePreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) { OppoPhoneApp() }
}
