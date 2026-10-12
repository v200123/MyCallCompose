package com.lastcoffee.top.oppo_call_compose.feature.call

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lastcoffee.top.oppo_call_compose.data.demoCalls
import com.lastcoffee.top.oppo_call_compose.data.demoContacts
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallButtonBackground
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallEndRed
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallSelectedBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CallSessionState {
    DIALING, WAITING_FOR_ANSWER, PLAYING_OUTCOME, ACTIVE, ON_HOLD, DISCONNECTING, DISCONNECTED
}

/** In-app call preview with local state transitions. */
@Composable
internal fun InCallScreen(number: String, onCallFinished: suspend (ManualCall) -> Unit) {
    var callState by remember(number) { mutableStateOf(CallSessionState.DIALING) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var dialpadOpen by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var connectedAt by remember(number) { mutableStateOf<Long?>(null) }
    val callStartedAtMillis = remember(number) { System.currentTimeMillis() }
    var durationAtEnd by remember(number) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val ringbackPrefs = remember(context) { context.getSharedPreferences("call_scenario_media", 0) }
    var ringbackVideoUri by remember { mutableStateOf(ringbackPrefs.getString("ringback_video_uri", null)?.let(Uri::parse)) }
    var ringbackVideoError by remember { mutableStateOf(false) }
    var callOutcome by remember(number) { mutableStateOf<CallOutcome?>(null) }
    var outcomeCycle by remember(number) { mutableIntStateOf(0) }
    var outgoingMenuVisible by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (persisted) {
                ringbackPrefs.edit().putString("ringback_video_uri", uri.toString()).apply()
                ringbackVideoError = false
                ringbackVideoUri = uri
            } else {
                ringbackVideoError = true
            }
        }
    }
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
        if (callState != CallSessionState.DISCONNECTING && callState != CallSessionState.DISCONNECTED) {
            durationAtEnd = connectedAt?.let { start ->
                ((SystemClock.elapsedRealtime() - start) / 1_000).toInt().coerceAtLeast(1)
            } ?: 0
            recording = false
            dialpadOpen = false
            dtmfDigits = ""
            callState = CallSessionState.DISCONNECTING
        }
    }

    fun playOutcome(outcome: CallOutcome) {
        if (callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER) {
            outgoingMenuVisible = false
            callOutcome = outcome
            outcomeCycle = 0
            callState = CallSessionState.PLAYING_OUTCOME
        }
    }

    BackHandler {
        if (dialpadOpen) dialpadOpen = false else requestHangup()
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val wasLightStatusBar = bars?.isAppearanceLightStatusBars
        val wasLightNavigationBar = bars?.isAppearanceLightNavigationBars
        val wasNavigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else null
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (wasLightStatusBar != null) bars?.isAppearanceLightStatusBars = wasLightStatusBar
            if (wasLightNavigationBar != null) bars?.isAppearanceLightNavigationBars = wasLightNavigationBar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wasNavigationBarContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = wasNavigationBarContrastEnforced
            }
        }
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val originalAttributes = window?.attributes
        val originalWindowBackground = window?.decorView?.background
        if (window != null) {
            // Current Android versions do not let ordinary apps read the
            // wallpaper bitmap. Show the system wallpaper behind this
            // transparent call window and blur that layer through WindowManager.
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            )
            window.setDimAmount(0.18f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.attributes = window.attributes.apply { blurBehindRadius = 120 }
            }
        }
        onDispose {
            if (window != null && originalAttributes != null) {
                window.attributes = originalAttributes
                window.setBackgroundDrawable(originalWindowBackground)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isResumed = true
            if (event == Lifecycle.Event.ON_PAUSE) isResumed = false
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(callState) {
        if (callState == CallSessionState.ACTIVE && !microphoneGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(callState, microphoneGranted, isResumed) {
        if (callState == CallSessionState.ACTIVE && microphoneGranted && isResumed) {
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

    DisposableEffect(callState, callOutcome, isResumed) {
        val outcome = callOutcome
        if (callState == CallSessionState.PLAYING_OUTCOME && outcome != null && isResumed) {
            val player = CallOutcomePlayer(
                context = context,
                outcome = outcome,
                onCycle = { outcomeCycle = it },
                onFinished = ::requestHangup,
            )
            onDispose { player.stop() }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(callState) {
        when (callState) {
            CallSessionState.DIALING -> {
                delay(1_200)
                callState = CallSessionState.WAITING_FOR_ANSWER
            }
            CallSessionState.DISCONNECTING -> {
                delay(500)
                callState = CallSessionState.DISCONNECTED
            }
            CallSessionState.DISCONNECTED -> {
                delay(1_500)
                onCallFinished(
                    ManualCall(
                        number = number,
                        kind = CallKind.OUTGOING,
                        durationSeconds = durationAtEnd,
                        timeMillis = callStartedAtMillis,
                        wasConnected = connectedAt != null,
                    ),
                )
            }
            else -> Unit
        }
    }

    // The document picker backgrounds the activity; choosing a video must not consume
    // the answer window or finish the call behind the picker.
    LaunchedEffect(callState, isResumed) {
        if (callState == CallSessionState.WAITING_FOR_ANSWER && isResumed) {
            delay(30_000)
            playOutcome(CallOutcome.NO_ANSWER)
        }
    }

    LaunchedEffect(connectedAt, callState) {
        val start = connectedAt
        if (start != null && (callState == CallSessionState.ACTIVE || callState == CallSessionState.ON_HOLD)) {
            while (true) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - start) / 1_000).toInt()
                delay(250)
            }
        }
    }

    val canUseCallControls = callState == CallSessionState.ACTIVE
    val isOutgoing = callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER
    val canUseSpeaker = callState == CallSessionState.DIALING ||
        callState == CallSessionState.WAITING_FOR_ANSWER || canUseCallControls || callState == CallSessionState.ON_HOLD
    val canHangUp = callState != CallSessionState.DISCONNECTING && callState != CallSessionState.DISCONNECTED

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().drawWithContent {
        // The call Activity switches from an opaque page to a transparent
        // wallpaper window. Clear the previous frame before drawing controls.
        drawRect(Color.Transparent, blendMode = BlendMode.Clear)
        drawContent()
    }) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (isOutgoing && isResumed && ringbackVideoUri != null && !ringbackVideoError) {
            key(ringbackVideoUri) {
                RingbackVideo(
                    uri = ringbackVideoUri!!,
                    onError = { ringbackVideoError = true },
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)))
        }
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
                        Text(displayName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        displayNumber?.let {
                            Text(it, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(location, color = Color(0x99FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        CallSessionState.DIALING -> "正在呼叫..."
                        CallSessionState.WAITING_FOR_ANSWER -> "等待接听"
                        CallSessionState.PLAYING_OUTCOME -> callOutcome?.status ?: "未接通"
                        CallSessionState.ACTIVE -> "正在通话"
                        CallSessionState.ON_HOLD -> "保持"
                        CallSessionState.DISCONNECTING -> "正在挂断..."
                        CallSessionState.DISCONNECTED -> callOutcome?.status ?: if (connectedAt == null) "未接通" else "通话结束"
                    }
                    Text(status, color = Color.White, fontSize = 18.sp)
                    if (connectedAt != null && callState != CallSessionState.DISCONNECTING) {
                        Text("  %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = Color.White, fontSize = 18.sp)
                    }
                }
                if (callState == CallSessionState.PLAYING_OUTCOME) {
                    Text(
                        if (callOutcome == CallOutcome.NO_ANSWER) "正在播放提示音 ${outcomeCycle.coerceAtLeast(1)}/2" else "正在播放提示音",
                        color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (isOutgoing && ringbackVideoError) {
                    Text("彩铃视频无法播放，请重新选择", color = Color(0xFFFFC9C9), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (callState == CallSessionState.WAITING_FOR_ANSWER) {
                    TextButton(onClick = {
                        connectedAt = SystemClock.elapsedRealtime()
                        elapsedSeconds = 0
                        callState = CallSessionState.ACTIVE
                        val vibrator = context.getSystemService(Vibrator::class.java)
                        if (vibrator?.hasVibrator() == true) {
                            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }) {
                        Text("接通", color = Color(0xFFB5E8B8))
                    }
                }
                if (callState == CallSessionState.ACTIVE) {
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
                        InCallAction("拨号盘", Icons.Filled.Dialpad, false, enabled = canUseCallControls) {
                            dtmfDigits = ""
                            dialpadOpen = true
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (isOutgoing) {
                            InCallAction("暂停", Icons.Filled.Pause, false, enabled = true) {
                                playOutcome(CallOutcome.NO_ANSWER)
                            }
                            InCallAction("视频", Icons.Filled.VideoCall, false, enabled = true) {
                                playOutcome(CallOutcome.BUSY)
                            }
                            Box {
                                InCallAction("更多", Icons.Filled.MoreVert, false, enabled = true) {
                                    outgoingMenuVisible = true
                                }
                                DropdownMenu(expanded = outgoingMenuVisible, onDismissRequest = { outgoingMenuVisible = false }) {
                                    DropdownMenuItem(text = { Text("选择彩铃视频") }, onClick = {
                                        outgoingMenuVisible = false
                                        videoPicker.launch(arrayOf("video/*"))
                                    })
                                    if (ringbackVideoUri != null) {
                                        DropdownMenuItem(text = { Text("移除彩铃视频") }, onClick = {
                                            outgoingMenuVisible = false
                                            ringbackPrefs.edit().remove("ringback_video_uri").apply()
                                            ringbackVideoUri = null
                                            ringbackVideoError = false
                                        })
                                    }
                                    DropdownMenuItem(text = { Text("空号") }, onClick = { playOutcome(CallOutcome.EMPTY_NUMBER) })
                                    DropdownMenuItem(text = { Text("关机") }, onClick = { playOutcome(CallOutcome.POWERED_OFF) })
                                }
                            }
                        } else {
                            InCallAction("保持", Icons.Filled.Pause, callState == CallSessionState.ON_HOLD,
                                enabled = canUseCallControls || callState == CallSessionState.ON_HOLD) {
                                if (callState == CallSessionState.ACTIVE) {
                                    recording = false
                                    dialpadOpen = false
                                    callState = CallSessionState.ON_HOLD
                                } else {
                                    callState = CallSessionState.ACTIVE
                                }
                            }
                            InCallAction("录音", Icons.Filled.FiberManualRecord, recording, enabled = canUseCallControls) { recording = !recording }
                            InCallAction("添加通话", Icons.Filled.Call, false, enabled = false) { }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(top = 30.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(
                    onClick = ::requestHangup,
                    enabled = canHangUp,
                    modifier = Modifier.size(70.dp).clip(CircleShape)
                        .background(if (canHangUp) InCallEndRed else InCallButtonBackground),
                ) {
                    Icon(
                        Icons.Filled.CallEnd,
                        contentDescription = "挂断",
                        tint = if (canHangUp) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    "挂断",
                    color = if (canHangUp) Color.White else Color(0x66FFFFFF),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
      }
    }
}

@Composable
private fun RingbackVideo(uri: Uri, onError: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    onError()
                    true
                }
                setVideoURI(uri)
            }
        },
        onRelease = { it.stopPlayback() },
    )
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
