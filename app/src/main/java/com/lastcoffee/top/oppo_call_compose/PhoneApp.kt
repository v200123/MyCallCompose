package com.lastcoffee.top.oppo_call_compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.delay

private val MissedRed = Color(0xFFD4483A)
private val SecondaryText = Color(0xFF858585)
private val DividerColor = Color(0xFFEEEEEE)
private val TagOrange = Color(0xFFFF7926)
private val NavigationSelected = Color(0xE6000000)
private val NavigationUnselected = Color(0x8A000000)
private val InCallBackground = Color(0xFF101010)
private val InCallButtonBackground = Color(0x29999999)
private val InCallSelectedBackground = Color(0xFFE6E6E6)
private val InCallEndRed = Color(0xFFE74D4D)

// The supplied design is 1080 px wide at 2x density, so its Compose baseline is 540 dp.
private val DesignSegmentWidth = 196.dp

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    CALLS("通话", Icons.Filled.Phone),
    CONTACTS("联系人", Icons.Filled.Contacts),
    STORE("营业厅", Icons.Filled.Storefront),
}

private data object HomeRoute
private data object SettingsRoute
private data class CallDetailRoute(val record: CallRecord)
private data class InCallRoute(val number: String)

@Composable
fun OppoPhoneApp(onDial: (String) -> Unit = {}) {
    val context = LocalContext.current
    val manualCallStore = remember(context) { ManualCallStore(context) }
    val manualCalls by manualCallStore.calls.collectAsState(initial = emptyList())
    LaunchedEffect(manualCallStore) { manualCallStore.importLegacyRecords() }
    val records = manualCalls.map { it.toCallRecord() } + demoCalls
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                PhoneHomeScreen(
                    records = records,
                    onShowDetail = { backStack.add(CallDetailRoute(it)) },
                    onStartCall = { backStack.add(InCallRoute(it)) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    onDial = onDial,
                )
            }
            entry<SettingsRoute> {
                CallSettingsScreen(
                    manualCount = manualCalls.size,
                    onBack = { backStack.removeLastOrNull() },
                    onSave = { newCall ->
                        try {
                            manualCallStore.add(newCall)
                            backStack.removeLastOrNull()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    },
                )
            }
            entry<CallDetailRoute> { route ->
                CallDetailScreen(
                    record = route.record,
                    allRecords = records,
                    onBack = { backStack.removeLastOrNull() },
                    onDial = { number -> backStack.add(InCallRoute(number)) },
                )
            }
            entry<InCallRoute> { route ->
                InCallScreen(number = route.number, onEndCall = { backStack.removeLastOrNull() })
            }
        },
    )
}

@Composable
private fun PhoneHomeScreen(
    records: List<CallRecord>,
    onShowDetail: (CallRecord) -> Unit,
    onStartCall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDial: (String) -> Unit,
) {
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
                        onOpenSettings = onOpenSettings,
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
private fun CallDetailScreen(record: CallRecord, allRecords: List<CallRecord>, onBack: () -> Unit, onDial: (String) -> Unit) {
    val isEmergency = record.number == "紧急呼叫中心"
    val contact = remember(record) { demoContacts.firstOrNull { it.number == record.number } }
    val displayName = if (isEmergency) "紧急呼叫中心" else contact?.name ?: record.number
    val number = if (isEmergency) record.description else contact?.number
        ?: record.description.takeIf { it.all(Char::isDigit) } ?: record.number
    val normalizedDescription = record.description.replace("\n", "  ")
    val location = contact?.location ?: when {
        isEmergency -> ""
        normalizedDescription.contains("\n") -> normalizedDescription.substringAfter("\n").trim()
        else -> normalizedDescription.removePrefix("小布通话").trim()
    }
    val records = remember(record, allRecords) {
        if (isEmergency) listOf(
            CallHistoryEntry("2026/9/27 21:58", CallKind.OUTGOING, "未接通"),
            CallHistoryEntry("2026/9/17 16:30", CallKind.OUTGOING, "未接通"),
            CallHistoryEntry("2026/9/17 16:27", CallKind.OUTGOING, "未接通"),
        ) else allRecords.filter { it.number == record.number }.map { historyRecord ->
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
        }.ifEmpty { listOf(CallHistoryEntry("${record.date} 17:22", record.kind, "呼出  未接通")) }
    }
    var menuExpanded by remember { mutableStateOf(false) }

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
                    IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                        Image(painterResource(R.drawable.ic_detail_add_contact), contentDescription = "添加联系人", modifier = Modifier.size(28.dp))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(28.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("编辑号码") }, onClick = { menuExpanded = false })
                            DropdownMenuItem(text = { Text("删除通话记录") }, onClick = { menuExpanded = false })
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
                        Text("查看全部", fontSize = 18.sp, color = Color(0xFF249642), modifier = Modifier.clickable { })
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(18.dp)).background(Color.White),
                    ) {
                        records.forEachIndexed { index, entry ->
                            CallHistoryItem(entry)
                            if (index < records.lastIndex) {
                                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 48.dp, end = 22.dp))
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(22.dp)) }
            }
            if (!isEmergency) {
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

private enum class DemoCallState {
    DIALING, WAITING_FOR_ANSWER, ACTIVE, ON_HOLD, DISCONNECTING, DISCONNECTED
}

/** Local call preview. A real Telecom connection must drive these transitions in a dialer build. */
@Composable
private fun InCallScreen(number: String, onEndCall: () -> Unit) {
    var callState by remember(number) { mutableStateOf(DemoCallState.DIALING) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var dialpadOpen by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var connectedAt by remember(number) { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val hangupPhrase = remember(context) { runCatching { voiceHangupPhrase(context) }.getOrDefault("语音口令") }
    var microphoneGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
    }
    val lifecycleOwner = context as? LifecycleOwner
    var isResumed by remember { mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    val contact = remember(number) { demoContacts.firstOrNull { it.number == number } }
    val callRecord = remember(number) { demoCalls.firstOrNull { it.description == number } }
    val displayName = contact?.name ?: callRecord?.number ?: number
    val displayNumber = contact?.number ?: if (callRecord != null) number else null
    val location = contact?.location ?: if (callRecord != null) "紧急号码" else "未知归属地"

    fun requestHangup() {
        if (callState != DemoCallState.DISCONNECTING && callState != DemoCallState.DISCONNECTED) {
            recording = false
            dialpadOpen = false
            dtmfDigits = ""
            callState = DemoCallState.DISCONNECTING
        }
    }

    BackHandler { requestHangup() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isResumed = true
            if (event == Lifecycle.Event.ON_PAUSE) isResumed = false
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(callState) {
        if (callState == DemoCallState.ACTIVE && !microphoneGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(callState, microphoneGranted, isResumed) {
        if (callState == DemoCallState.ACTIVE && microphoneGranted && isResumed) {
            voiceFailed = false
            val detector = VoiceHangupDetector(
                context = context.applicationContext,
                onListening = { voiceListening = true },
                onKeyword = { requestHangup() },
                onFailure = {
                    voiceListening = false
                    voiceFailed = true
                },
            )
            detector.start()
            onDispose {
                detector.stop()
                voiceListening = false
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(callState) {
        when (callState) {
            DemoCallState.DIALING -> {
                delay(1_200)
                callState = DemoCallState.WAITING_FOR_ANSWER
            }
            DemoCallState.WAITING_FOR_ANSWER -> {
                delay(30_000)
                callState = DemoCallState.DISCONNECTED
            }
            DemoCallState.DISCONNECTING -> {
                delay(500)
                callState = DemoCallState.DISCONNECTED
            }
            DemoCallState.DISCONNECTED -> {
                delay(1_500)
                onEndCall()
            }
            else -> Unit
        }
    }

    LaunchedEffect(connectedAt, callState) {
        val start = connectedAt
        if (start != null && (callState == DemoCallState.ACTIVE || callState == DemoCallState.ON_HOLD)) {
            while (true) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - start) / 1_000).toInt()
                delay(250)
            }
        }
    }

    val canUseCallControls = callState == DemoCallState.ACTIVE
    val canUseSpeaker = callState == DemoCallState.DIALING ||
        callState == DemoCallState.WAITING_FOR_ANSWER || canUseCallControls || callState == DemoCallState.ON_HOLD
    val canHangUp = callState != DemoCallState.DISCONNECTING && callState != DemoCallState.DISCONNECTED

    Surface(color = InCallBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = 92.dp),
            ) {
                // call_card_info.xml keeps the 70dp avatar at the end of the name/number block,
                // rather than centering it above the caller information.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        displayNumber?.let {
                            Text(it, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                        }
                        Text(location, color = Color(0x99FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                    }
                    Box(
                        modifier = Modifier.padding(start = 16.dp).size(70.dp).clip(CircleShape).background(Color(0xFF535353)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "联系人头像", tint = Color.White, modifier = Modifier.size(46.dp))
                    }
                }
                Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val status = when (callState) {
                        DemoCallState.DIALING -> "正在呼叫"
                        DemoCallState.WAITING_FOR_ANSWER -> "等待接听"
                        DemoCallState.ACTIVE -> "通话中"
                        DemoCallState.ON_HOLD -> "通话保持"
                        DemoCallState.DISCONNECTING -> "正在挂断"
                        DemoCallState.DISCONNECTED -> if (connectedAt == null) "未接通" else "通话结束"
                    }
                    Text(status, color = Color.White, fontSize = 18.sp)
                    if (connectedAt != null && callState != DemoCallState.DISCONNECTING) {
                        Text("  %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = Color.White, fontSize = 18.sp)
                    }
                }
                if (callState == DemoCallState.WAITING_FOR_ANSWER) {
                    TextButton(onClick = {
                        connectedAt = SystemClock.elapsedRealtime()
                        elapsedSeconds = 0
                        callState = DemoCallState.ACTIVE
                        val vibrator = context.getSystemService(Vibrator::class.java)
                        if (vibrator?.hasVibrator() == true) {
                            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }) {
                        Text("模拟接通", color = Color(0xFFB5E8B8))
                    }
                }
                if (callState == DemoCallState.ACTIVE) {
                    if (!microphoneGranted) {
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text("开启语音挂断", color = Color(0xFFB5E8B8))
                        }
                    } else {
                        Text(
                            when {
                                voiceFailed -> "语音挂断不可用"
                                voiceListening -> "语音挂断已开启：$hangupPhrase"
                                else -> "正在准备语音挂断"
                            },
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (dialpadOpen) {
                InCallDtmfPad(digits = dtmfDigits, onDigit = { dtmfDigits += it }, onClose = { dialpadOpen = false })
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        InCallAction("静音", Icons.Filled.MicOff, muted, enabled = canUseCallControls) { muted = !muted }
                        InCallAction("扬声器", Icons.Filled.VolumeUp, speakerOn, enabled = canUseSpeaker) { speakerOn = !speakerOn }
                        InCallAction("拨号键盘", Icons.Filled.Dialpad, false, enabled = canUseCallControls) {
                            dtmfDigits = ""
                            dialpadOpen = true
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        InCallAction("保持", Icons.Filled.Pause, callState == DemoCallState.ON_HOLD,
                            enabled = canUseCallControls || callState == DemoCallState.ON_HOLD) {
                            if (callState == DemoCallState.ACTIVE) {
                                recording = false
                                dialpadOpen = false
                                callState = DemoCallState.ON_HOLD
                            } else {
                                callState = DemoCallState.ACTIVE
                            }
                        }
                        InCallAction("录音", Icons.Filled.FiberManualRecord, recording, enabled = canUseCallControls) { recording = !recording }
                        InCallAction("添加通话", Icons.Filled.Call, false, enabled = false) { }
                    }
                }
            }
            IconButton(
                onClick = ::requestHangup,
                enabled = canHangUp,
                modifier = Modifier.padding(top = 30.dp, bottom = 28.dp).size(68.dp).clip(CircleShape).background(InCallEndRed),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "挂断", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun InCallAction(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.width(88.dp).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(66.dp).clip(CircleShape).background(if (enabled && selected) InCallSelectedBackground else InCallButtonBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(36.dp), tint = if (!enabled) Color(0x66FFFFFF) else if (selected) Color.Black else Color.White)
        }
        Text(label, color = if (enabled) Color.White else Color(0x66FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun InCallDtmfPad(digits: String, onDigit: (String) -> Unit, onClose: () -> Unit) {
    val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(digits, color = Color.White, fontSize = 20.sp, maxLines = 1, modifier = Modifier.height(28.dp))
        keys.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    Column(modifier = Modifier.width(78.dp).height(54.dp).clickable { onDigit(digit) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(digit, color = Color.White, fontSize = 28.sp, lineHeight = 30.sp)
                        Text(letters, color = Color(0xFFB8B8B8), fontSize = 10.sp)
                    }
                }
            }
        }
        Text("收起拨号键盘", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClose))
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
    onOpenSettings: () -> Unit,
) {
    var missedOnly by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
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
                    onSearchClick = onShowDialPad,
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
                modifier = Modifier.width(DesignSegmentWidth).align(Alignment.Center),
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(24.dp))
                }
                Box {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(text = { Text("通话设置") }, onClick = {
                            onDismissMenu()
                            onSettingsClick()
                        })
                        DropdownMenuItem(text = { Text("清除通话记录") }, onClick = onDismissMenu)
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
        modifier = modifier.size(width = 106.dp, height = 40.dp).shadow(6.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BD13)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(Icons.Filled.Dialpad, contentDescription = "打开拨号盘", modifier = Modifier.size(24.dp), tint = Color.White)
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
        modifier = Modifier.fillMaxWidth().height(346.dp).shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFFFEFEFE),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Color(0xFFC5C5C5)))
            dialKeys.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().clickable { onNumberChange(number + key.digit) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(key.digit, fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 29.sp)
                            Text(key.letters, fontSize = 10.sp, color = SecondaryText, lineHeight = 11.sp)
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

@Preview(showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun OppoPhonePreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) { OppoPhoneApp() }
}
