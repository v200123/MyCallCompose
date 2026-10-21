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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.data.demoContacts
import com.lastcoffee.top.oppo_call_compose.data.demoCalls
import com.lastcoffee.top.oppo_call_compose.feature.contacts.ContactsScreen
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.ui.common.MissedRed
import com.lastcoffee.top.oppo_call_compose.ui.common.NavigationSelected
import com.lastcoffee.top.oppo_call_compose.ui.common.NavigationUnselected
import com.lastcoffee.top.oppo_call_compose.ui.common.SecondaryText
import com.lastcoffee.top.oppo_call_compose.ui.common.TagOrange
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDivider
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDividerColor
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoDividerHeight
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoGreen
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoPrimary
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoSecondary
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoIcon
import com.lastcoffee.top.oppo_call_compose.ui.common.oppoTextStyle
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.launch

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    CALLS("通话", Icons.Filled.Phone),
    CONTACTS("联系人", Icons.Filled.Contacts),
    STORE("营业厅", Icons.Filled.Storefront),
}

@Composable
internal fun PhoneHomeScreen(
    records: List<CallRecord>,
    locationFor: (String) -> String?,
    onLookupLocation: (String) -> Unit = {},
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
    LaunchedEffect(dialedNumber) { onLookupLocation(dialedNumber) }
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
                        locationFor = locationFor,
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
    locationFor: (String) -> String?,
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
    val headerTravelPx = with(LocalDensity.current) { 55.dp.toPx() }
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
                DialSearchResults(number = dialedNumber, records = records, onInfo = onShowDetail, locationFor = locationFor)
            } else {
                CallList(
                    records = if (missedOnly) records.filter { it.isUnanswered } else records,
                    locationFor = locationFor,
                    state = listState,
                    onInfo = onShowDetail,
                    contentBottomPadding = if (dialPadVisible) 370.dp else 72.dp,
                )
            }
        }

        if (!dialPadVisible) {
            DialPadFab(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp),
                onClick = onShowDialPad,
            )
        }

        AnimatedVisibility(
            visible = dialPadVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OppoDialPad(
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
            .height(107.dp - 55.dp * collapseProgress)
            .background(Color.White),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            // FloatingTabFrameLayout reserves the action area on both sides of the tabs:
            // two 48dp IconButtons plus the toolbar's 8dp end padding.
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                SegmentedFilter(missedOnly, onFilterChange)
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Image(painterResource(R.drawable.oppo_pb_ic_menu_search_normal), contentDescription = "搜索", modifier = Modifier.size(24.dp))
                }
                Box {
                    IconButton(onClick = onMenuClick) { Image(painterResource(R.drawable.oppo_coui_toolbar_menu_icon_more_normal), contentDescription = "更多", modifier = Modifier.size(24.dp)) }
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
            modifier = Modifier.offset(x = 16.dp, y = 54.dp * (1f - collapseProgress)).height(53.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("通话", style = oppoTextStyle((32f - 14f * collapseProgress).sp, weight = FontWeight.Medium), maxLines = 1)
        }
        HorizontalDivider(
            color = OppoDividerColor.copy(alpha = OppoDividerColor.alpha * collapseProgress),
            thickness = OppoDividerHeight,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SegmentedFilter(missedOnly: Boolean, onFilterChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // FloatingTabView: equal item widths = max(widest padded label, 64dp),
    // with 2dp on either side. The parent can constrain this on narrow screens.
    Row(
        modifier = modifier.width(IntrinsicSize.Max).height(32.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9E9E9)).padding(2.dp),
    ) {
        FilterChoice("全部", selected = !missedOnly, modifier = Modifier.weight(1f).widthIn(min = 64.dp)) { onFilterChange(false) }
        FilterChoice("未接", selected = missedOnly, modifier = Modifier.weight(1f).widthIn(min = 64.dp)) { onFilterChange(true) }
    }
}

@Composable
private fun FilterChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = oppoTextStyle(14.sp, if (selected) OppoPrimary else OppoSecondary, FontWeight.Medium))
    }
}

@Composable
private fun CallList(
    records: List<CallRecord>,
    locationFor: (String) -> String?,
    state: androidx.compose.foundation.lazy.LazyListState,
    onInfo: (CallRecord) -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(records) { index, record ->
            CallRow(record = record, location = locationFor(record.number) ?: locationFor(record.description), onInfo = { onInfo(record) }, showDivider = index < records.lastIndex)
        }
        item { Spacer(modifier = Modifier.height(contentBottomPadding)) }
    }
}

@Composable
private fun CallRow(record: CallRecord, location: String?, onInfo: () -> Unit, showDivider: Boolean = true, query: String = "") {
    val primaryColor = if (record.isUnanswered) MissedRed else OppoPrimary
    // call_log_list_item.xml: icon at 16dp; number starts after 16dp icon + 8dp.
    // The divider spans the whole row, independently of the date and info button.
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(record.kind.oppoIcon), null,
                tint = if (record.isUnanswered) MissedRed else Color.Unspecified,
                modifier = Modifier.padding(top = 12.dp).size(16.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, end = 5.dp, bottom = 10.dp)) {
                val label = buildAnnotatedString {
                    val start = if (query.isBlank()) -1 else record.number.indexOf(query)
                    if (start < 0) append(record.number) else {
                        append(record.number.take(start))
                        withStyle(SpanStyle(color = OppoGreen)) { append(record.number.substring(start, start + query.length)) }
                        append(record.number.drop(start + query.length))
                    }
                }
                Text(label, style = oppoTextStyle(16.sp, primaryColor, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(location ?: record.description, Modifier.padding(top = 2.dp), style = oppoTextStyle(14.sp, OppoSecondary))
                record.tag?.let { Text(it, style = oppoTextStyle(14.sp, TagOrange)) }
            }
            Text(record.date, Modifier.align(Alignment.CenterVertically), style = oppoTextStyle(14.sp, OppoSecondary))
            Box(Modifier.align(Alignment.CenterVertically).padding(end = 6.dp).width(44.dp).height(64.dp).clickable(onClick = onInfo), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.oppo_pb_dr_detail_normal), "详情", Modifier.size(24.dp))
            }
        }
        if (showDivider) OppoDivider(start = 41.dp, end = 16.dp, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DialSearchResults(number: String, records: List<CallRecord>, onInfo: (CallRecord) -> Unit, locationFor: (String) -> String?) {
    val contacts = remember(number) { demoContacts.filter { it.number.filter(Char::isDigit).startsWith(number) } }
    val matches = remember(records, contacts, number) {
        val contactNumbers = contacts.map { it.number.filter(Char::isDigit) }.toSet()
        records.filter { record ->
            val digits = record.searchPhoneNumber()
            digits.startsWith(number) && digits !in contactNumbers
        }.distinctBy { it.searchPhoneNumber() }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // DialtactsFragment replaces the XML's initial 70dp margin with the measured
        // app bar height. Input hides its title and actions, leaving the 52dp toolbar.
        Spacer(modifier = Modifier.height(52.dp))
        val resultsState = rememberLazyListState()
        val location = if (number.length in 7..20) {
            locationFor(number) ?: contacts.firstOrNull()?.location
        } else null
        OppoDialNumberHeader(number, location, showDivider = resultsState.canScrollBackward)
        // dialpad_add_contact starts 116.33dp below the content top.
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = resultsState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(matches) { index, record ->
                DialSearchRow(
                    name = record.number.takeIf { it.any(Char::isLetter) },
                    number = record.searchPhoneNumber(),
                    location = locationFor(record.searchPhoneNumber())
                        ?: record.description.takeUnless { it.any(Char::isDigit) },
                    onInfo = { onInfo(record) },
                    showDivider = index < matches.lastIndex || contacts.isNotEmpty(),
                    query = number,
                    recentCall = true,
                )
            }
            itemsIndexed(contacts) { index, contact ->
                DialSearchRow(
                    name = contact.name,
                    number = contact.number,
                    location = locationFor(contact.number) ?: contact.location,
                    query = number,
                    onInfo = { onInfo(CallRecord(contact.number, contact.location, "联系人", CallKind.INCOMING)) },
                    showDivider = index < contacts.lastIndex,
                )
            }
            if (matches.isEmpty() && contacts.isEmpty()) {
                item {
                    listOf("新建联系人" to R.drawable.oppo_pb_ic_add_contact,
                        "保存到已有联系人" to R.drawable.oppo_pb_ic_save_to_exist_contact).forEachIndexed { index, (label, drawable) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(drawable), null, Modifier.size(24.dp))
                            Text(label, Modifier.padding(start = 16.dp), style = oppoTextStyle(16.sp, weight = FontWeight.Medium))
                        }
                        if (index == 0) OppoDivider(start = 56.dp, end = 16.dp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(354.dp)) }
        }
    }
}

private fun CallRecord.searchPhoneNumber(): String =
    (if (number.any(Char::isLetter)) description else number).filter(Char::isDigit)

@Composable
private fun DialSearchRow(
    name: String?,
    number: String,
    location: String?,
    query: String,
    onInfo: () -> Unit,
    showDivider: Boolean,
    recentCall: Boolean = false,
) {
    val displayNumber = remember(number) { formatDialNumber(number) }
    // CallLogAdapter.bindViewInSearchMode hides the call-type icon and replaces
    // call_time with attribution. The name/number starts at 16dp in this mode.
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp, end = 5.dp)) {
                if (name != null) {
                    Text(name, style = oppoTextStyle(16.sp, weight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HighlightedNumber(displayNumber, query)
                } else {
                    HighlightedNumber(displayNumber, query, primary = true)
                    if (recentCall) Text("最近通话", style = oppoTextStyle(14.sp, OppoSecondary))
                }
            }
            Text(location.orEmpty(), style = oppoTextStyle(14.sp, OppoSecondary),
                modifier = Modifier.widthIn(max = 100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            Box(Modifier.padding(end = 6.dp).size(width = 44.dp, height = 64.dp).clickable(onClick = onInfo), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.oppo_pb_dr_detail_normal), "详情", Modifier.size(24.dp))
            }
        }
        if (showDivider) OppoDivider(start = 41.dp, end = 16.dp)
    }
}

@Composable
private fun HighlightedNumber(number: String, query: String, primary: Boolean = false) {
    val digitIndices = number.indices.filter { number[it].isDigit() }
    val match = digitIndices.map(number::get).joinToString("").indexOf(query)
    val start = if (query.isNotEmpty() && match >= 0) digitIndices[match] else -1
    val end = if (start >= 0) digitIndices[match + query.length - 1] + 1 else -1
    val text = buildAnnotatedString {
        if (start < 0) append(number) else {
            append(number.take(start))
            withStyle(SpanStyle(color = OppoGreen)) { append(number.substring(start, end)) }
            append(number.drop(end))
        }
    }
    Text(text, style = if (primary) oppoTextStyle(16.sp, weight = FontWeight.Medium) else oppoTextStyle(14.sp, OppoSecondary),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Image(painterResource(R.drawable.oppo_pb_ic_floating_dial_show), contentDescription = "打开拨号盘", modifier = Modifier.size(36.dp))
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
                val drawable = when (tab) {
                    PhoneTab.CALLS -> if (selected) R.drawable.oppo_pb_dr_dialer_selected else R.drawable.oppo_pb_dr_dialer_normal
                    PhoneTab.CONTACTS -> if (selected) R.drawable.oppo_pb_dr_contacts_selected else R.drawable.oppo_pb_dr_contacts_normal
                    PhoneTab.STORE -> if (selected) R.drawable.oppo_pb_dr_business_selected else R.drawable.oppo_pb_dr_business_normal
                }
                Image(painterResource(drawable), contentDescription = tab.label, modifier = Modifier.size(24.dp))
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

@Preview(name = "OPPO 拨号主页", showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun PhoneHomePreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        PhoneHomeScreen(
            records = demoCalls,
            locationFor = { null },
            onShowDetail = {},
            onShowContact = {},
            onStartCall = {},
            onOpenSearch = {},
            onOpenSettings = {},
            pendingDialNumber = null,
            onDialRequestConsumed = {},
            onClearCalls = { true },
            onDial = {},
        )
    }
}

@Preview(name = "OPPO 拨号盘及匹配记录", showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun PhoneDialPadPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        PhoneHomeScreen(
            records = demoCalls,
            locationFor = { null },
            onShowDetail = {},
            onShowContact = {},
            onStartCall = {},
            onOpenSearch = {},
            onOpenSettings = {},
            pendingDialNumber = "136",
            onDialRequestConsumed = {},
            onClearCalls = { true },
            onDial = {},
        )
    }
}
