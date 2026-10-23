package com.lastcoffee.top.oppo_call_compose.feature.call

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.graphics.drawable.ColorDrawable
import java.io.File
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.data.demoCalls
import com.lastcoffee.top.oppo_call_compose.data.demoContacts
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallButtonId
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallVisualStyle
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.BadgeRule
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallButtonSurface
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallPreviewBackdrop
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadTone
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadToneInteractionSource
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.delay

private enum class CallSessionState {
    DIALING, WAITING_FOR_ANSWER, PLAYING_OUTCOME, ACTIVE, ON_HOLD, DISCONNECTING, DISCONNECTED
}

private fun CallStage.toSessionState() = when (this) {
    CallStage.DIALING -> CallSessionState.DIALING
    CallStage.WAITING -> CallSessionState.WAITING_FOR_ANSWER
    CallStage.OUTCOME -> CallSessionState.PLAYING_OUTCOME
    CallStage.ACTIVE -> CallSessionState.ACTIVE
    CallStage.HOLD -> CallSessionState.ON_HOLD
    CallStage.DISCONNECTING -> CallSessionState.DISCONNECTING
    CallStage.DISCONNECTED -> CallSessionState.DISCONNECTED
}

/** In-app call preview with local state transitions. */
@Composable
internal fun InCallScreen(
    number: String,
    mode: CallAudioMode,
    initialStatusId: String,
    configuration: CallFlowConfiguration,
    mediaEntries: List<CallMediaEntry>,
    extras: PhoneExtras = PhoneExtras(),
    resolvedLocation: String? = null,
    badgeRule: BadgeRule? = null,
    onRingbackStarted: (String) -> Unit = {},
    onCallFinished: suspend (ManualCall) -> Unit,
) {
    val initialRule = configuration.statuses.firstOrNull { it.id == initialStatusId } ?: configuration.statuses.first()
    var activeStatusId by remember(number) { mutableStateOf(initialRule.id) }
    var callState by remember(number) { mutableStateOf(initialRule.stage.toSessionState()) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var dialpadOpen by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var connectedAt by remember(number) { mutableStateOf<Long?>(null) }
    val callStartedAtMillis = remember(number) { System.currentTimeMillis() }
    val callStartedAtElapsed = remember(number) { SystemClock.elapsedRealtime() }
    var durationAtEnd by remember(number) { mutableIntStateOf(0) }
    val context = LocalContext.current
    var ringbackVideoError by remember { mutableStateOf(false) }
    var callOutcome by remember(number) { mutableStateOf(initialRule.outcome?.let { value -> CallOutcome.entries.firstOrNull { it.name == value } }) }
    var promptPlaying by remember(number) { mutableStateOf(false) }
    var outcomeCycle by remember(number) { mutableIntStateOf(0) }
    val isPreview = LocalInspectionMode.current
    val hangupPhrase = remember(context) { runCatching { voiceHangupPhrase(context) }.getOrDefault("语音口令") }
    var microphoneGranted by remember {
        mutableStateOf(isPreview || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceFailed by remember { mutableStateOf(false) }
    val permissionLauncher = if (isPreview) null else rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
    }
    val lifecycleOwner = context as? LifecycleOwner
    var isResumed by remember { mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    val contact = remember(number) { demoContacts.firstOrNull { it.number == number } }
    val callRecord = remember(number) { demoCalls.firstOrNull { it.description == number } }
    val activeRule = configuration.statuses.firstOrNull { it.id == activeStatusId } ?: initialRule
    val configuredPromptType = extras.promptFor(number)
    val promptRuleId = configuredPromptType?.let(extras::promptRuleFor)
    val promptTargetRule = promptRuleId?.let { selectedId ->
        configuration.statuses.firstOrNull { it.id == selectedId && it.stage == CallStage.OUTCOME }
    } ?: configuration.statuses.firstOrNull { it.id == configuredPromptType?.legacyStatusId && it.stage == CallStage.OUTCOME }
    val promptType = configuredPromptType
    val promptRuleActive = promptPlaying &&
        (activeStatusId == promptTargetRule?.id || (promptTargetRule == null && callState == CallSessionState.PLAYING_OUTCOME))
    val displayName = contact?.name ?: callRecord?.number ?: number
    val displayNumber = contact?.number ?: if (callRecord != null) number else null
    val location = extras.locationFor(number) ?: resolvedLocation ?: contact?.location ?: if (callRecord != null) "紧急号码" else "未知归属地"
    val mediaForMode = remember(mode, mediaEntries) { mediaEntries.filter { it.mode == mode && mode.acceptsMimeType(it.mimeType) && File(it.filePath).exists() } }
    val ringbackVideos = remember(mediaEntries) { mediaEntries.filter { it.mode == CallAudioMode.RINGBACK && it.mimeType.startsWith("video/") && File(it.filePath).isFile } }
    val randomRingbackId = remember(number, ringbackVideos) { ringbackVideos.randomOrNull()?.id }
    val currentMedia = remember(activeStatusId, activeRule.mediaEntryId, activeRule.playSoundOnce, mediaForMode, mediaEntries, extras, number, randomRingbackId, callState, promptPlaying) {
        if (activeRule.playSoundOnce || (promptType != null && (callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER))) null
        else if (promptRuleActive) mediaEntries.firstOrNull {
            it.id == activeRule.mediaEntryId && (it.mimeType.startsWith("audio/") || it.mimeType.startsWith("video/")) && File(it.filePath).isFile
        }
        else if (activeRule.stage in setOf(CallStage.DIALING, CallStage.WAITING) &&
            (mode == CallAudioMode.RINGBACK || extras.ringbackBindings.containsKey(normalizePhoneNumber(number)))) {
            val selected = if (extras.blocksRingback(number)) null else {
                val boundId = extras.ringbackBindings[normalizePhoneNumber(number)]
                boundId?.let { id -> ringbackVideos.firstOrNull { it.id == id } }
                    ?: if (mode == CallAudioMode.RINGBACK) {
                        extras.defaultRingbackId?.let { id -> ringbackVideos.firstOrNull { it.id == id } }
                            ?: randomRingbackId?.let { id -> ringbackVideos.firstOrNull { it.id == id } }
                    } else null
            }
            selected ?: mediaEntries.firstOrNull { it.mode == CallAudioMode.DEFAULT_TONE && it.mimeType.startsWith("audio/") && File(it.filePath).isFile }
        } else {
            mediaEntries.firstOrNull { it.id == activeRule.mediaEntryId && it.mode.isCallMode && it.mode.acceptsMimeType(it.mimeType) && File(it.filePath).isFile && (!extras.blocksRingback(number) || !it.mimeType.startsWith("video/")) }
                ?: mediaForMode.filter { !it.mimeType.startsWith("video/") }.randomOrNull()
        }
    }
    val currentSound = remember(activeRule.soundEntryId, activeRule.playSoundOnce, activeRule.soundMuted, mediaEntries, promptType, callState, promptPlaying) {
        if (!activeRule.playSoundOnce || activeRule.soundMuted || (promptType != null && (callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER))) null else mediaEntries.firstOrNull {
            it.id == activeRule.soundEntryId && it.mimeType.startsWith("audio/") && File(it.filePath).exists()
        }
    }
    var playingStatusSoundCount by remember { mutableIntStateOf(0) }
    val statusSoundPlayers = remember { mutableListOf<MediaPlayer>() }
    val promptUsesRuleAudio = promptRuleActive && (currentSound != null ||
        (currentMedia != null && (!currentMedia.mimeType.startsWith("video/") || !ringbackVideoError)))

    fun transitionToStatus(id: String) {
        val target = configuration.statuses.firstOrNull { it.id == id } ?: return
        activeStatusId = target.id
        callState = target.stage.toSessionState()
        callOutcome = target.outcome?.let { value -> CallOutcome.entries.firstOrNull { it.name == value } }
        if ((target.stage == CallStage.ACTIVE || target.stage == CallStage.HOLD) && connectedAt == null) {
            connectedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
        }
    }

    fun onConfiguredButton(id: CallButtonId, defaultAction: () -> Unit) {
        val rule = activeRule.buttons.firstOrNull { it.id == id } ?: return
        if (!rule.visible || !rule.enabled) return
        val targetId = rule.targetStatusId
        if (targetId != null) transitionToStatus(targetId) else defaultAction()
    }

    fun requestHangup() {
        if (callState != CallSessionState.DISCONNECTING && callState != CallSessionState.DISCONNECTED) {
            durationAtEnd = connectedAt?.let { start ->
                ((SystemClock.elapsedRealtime() - start) / 1_000).toInt().coerceAtLeast(1)
            } ?: 0
            recording = false
            dialpadOpen = false
            dtmfDigits = ""
            val disconnecting = configuration.statuses.firstOrNull { it.stage == CallStage.DISCONNECTING }
            if (disconnecting != null) transitionToStatus(disconnecting.id) else callState = CallSessionState.DISCONNECTING
        }
    }

    fun playOutcome(outcome: CallOutcome) {
        if (callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER) {
            callOutcome = outcome
            outcomeCycle = 0
            val outcomeRule = configuration.statuses.firstOrNull { it.outcome == outcome.name }
                ?: configuration.statuses.firstOrNull { it.stage == CallStage.OUTCOME }
            if (outcomeRule != null) transitionToStatus(outcomeRule.id) else callState = CallSessionState.PLAYING_OUTCOME
        }
    }

    BackHandler {
        if (dialpadOpen) dialpadOpen = false else requestHangup()
    }

    val autoHangUpSeconds = extras.autoHangUpSecondsFor(number)
    LaunchedEffect(number, connectedAt, autoHangUpSeconds, callState) {
        val connectedAtMillis = connectedAt ?: return@LaunchedEffect
        val delaySeconds = autoHangUpSeconds ?: return@LaunchedEffect
        if (callState != CallSessionState.ACTIVE && callState != CallSessionState.ON_HOLD) return@LaunchedEffect
        val remainingMillis = (connectedAtMillis + delaySeconds * 1_000L - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        delay(remainingMillis)
        requestHangup()
    }

    LaunchedEffect(number, promptType, promptRuleId, callState, promptPlaying) {
        val type = promptType ?: return@LaunchedEffect
        if (promptPlaying || (callState != CallSessionState.DIALING && callState != CallSessionState.WAITING_FOR_ANSWER)) return@LaunchedEffect
        val remaining = (callStartedAtElapsed + type.delayMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        delay(remaining)
        if (callState != CallSessionState.DIALING && callState != CallSessionState.WAITING_FOR_ANSWER) return@LaunchedEffect
        val target = promptTargetRule
        promptPlaying = true
        val fallbackOutcome = when (type) {
            CallPromptType.POWER_OFF -> CallOutcome.POWERED_OFF
            CallPromptType.EMPTY_NUMBER -> CallOutcome.EMPTY_NUMBER
            CallPromptType.BUSY -> CallOutcome.BUSY
            CallPromptType.UNREACHABLE -> CallOutcome.UNREACHABLE
        }
        if (target != null) transitionToStatus(target.id) else callState = CallSessionState.PLAYING_OUTCOME
        callOutcome = target?.outcome?.let { value -> CallOutcome.entries.firstOrNull { it.name == value } } ?: fallbackOutcome
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

    LaunchedEffect(activeStatusId, callState) {
        if ((activeRule.stage == CallStage.ACTIVE || activeRule.stage == CallStage.HOLD) && connectedAt == null) {
            connectedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
        }
        if (!isPreview && callState == CallSessionState.ACTIVE && !microphoneGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(callState, microphoneGranted, isResumed) {
        if (!isPreview && callState == CallSessionState.ACTIVE && microphoneGranted && isResumed) {
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

    DisposableEffect(activeStatusId, callState, callOutcome, currentMedia?.filePath, promptRuleActive, isResumed) {
        val outcome = callOutcome
        if (callState == CallSessionState.PLAYING_OUTCOME && outcome != null && currentMedia == null && isResumed && !activeRule.playSoundOnce && !promptRuleActive) {
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

    DisposableEffect(activeStatusId, callState, promptRuleActive, promptType, promptUsesRuleAudio, isResumed) {
        if (callState == CallSessionState.PLAYING_OUTCOME && promptRuleActive && promptType != null && !promptUsesRuleAudio && isResumed) {
            val rawId = when (promptType) {
                CallPromptType.POWER_OFF -> R.raw.audio_power_off
                CallPromptType.EMPTY_NUMBER -> R.raw.audio_empty_number
                CallPromptType.BUSY -> R.raw.audio_user_busy
                CallPromptType.UNREACHABLE -> R.raw.audio_busy
            }
            val player = MediaPlayer.create(context, rawId)
            if (player == null) {
                requestHangup()
                onDispose { }
            } else {
                player.setOnCompletionListener {
                    if (activeRule.durationSeconds <= 0 || activeRule.timeoutTargetId == null) requestHangup()
                }
                player.setOnErrorListener { _, _, _ -> requestHangup(); true }
                runCatching { player.start() }.onFailure { requestHangup() }
                onDispose {
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    runCatching { if (player.isPlaying) player.stop() }
                    player.release()
                }
            }
        } else onDispose { }
    }

    LaunchedEffect(activeStatusId, isResumed, configuration.statuses) {
        val status = configuration.statuses.firstOrNull { it.id == activeStatusId } ?: return@LaunchedEffect
        if (!isResumed) return@LaunchedEffect
        if (status.stage == CallStage.DISCONNECTED) {
            delay(if (status.durationSeconds > 0) status.durationSeconds * 1_000L else 1_500L)
            onCallFinished(ManualCall(number = number, kind = CallKind.OUTGOING, durationSeconds = durationAtEnd, timeMillis = callStartedAtMillis, wasConnected = connectedAt != null))
        } else if (status.durationSeconds > 0) {
            delay(status.durationSeconds * 1_000L)
            status.timeoutTargetId?.let(::transitionToStatus)
        }
    }

    LaunchedEffect(activeStatusId, currentMedia?.filePath, isResumed) {
        val media = currentMedia
        if (media != null && media.mimeType.startsWith("audio/") && isResumed) {
            val player = MediaPlayer()
            runCatching {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC)
                player.setDataSource(media.filePath)
                player.isLooping = true
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            }.onFailure { player.release() }
            try {
                if (activeRule.durationSeconds > 0) delay(activeRule.durationSeconds * 1_000L) else kotlinx.coroutines.awaitCancellation()
            } finally {
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            statusSoundPlayers.toList().forEach { player ->
                runCatching { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.setOnPreparedListener(null) }
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
            statusSoundPlayers.clear()
        }
    }

    LaunchedEffect(activeStatusId, currentSound?.filePath, isResumed) {
        val sound = currentSound
        if (sound == null || !isResumed) return@LaunchedEffect
        val player = MediaPlayer()
        var released = false
        statusSoundPlayers += player
        playingStatusSoundCount += 1
        fun finish() {
            if (!released) {
                released = true
                statusSoundPlayers.remove(player)
                playingStatusSoundCount = (playingStatusSoundCount - 1).coerceAtLeast(0)
                runCatching { player.release() }
            }
        }
        runCatching {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setDataSource(sound.filePath)
            player.isLooping = false
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, _, _ -> finish(); true }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }.onFailure { finish() }
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

    val isOutgoing = callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER
    val canHangUp = callState != CallSessionState.DISCONNECTING && callState != CallSessionState.DISCONNECTED
    fun ruleFor(id: CallButtonId) = activeRule.buttons.firstOrNull { it.id == id } ?: com.lastcoffee.top.oppo_call_compose.model.CallButtonRule(id)
    val outgoingRow = callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().drawWithContent {
        // The call Activity switches from an opaque page to a transparent
        // wallpaper window. Clear the previous frame before drawing controls.
        drawRect(Color.Transparent, blendMode = BlendMode.Clear)
        drawContent()
    }) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (LocalInspectionMode.current) InCallPreviewBackdrop()
        val videoSource = currentMedia?.takeIf { it.mimeType.startsWith("video/") && (isOutgoing || promptRuleActive) }?.filePath?.let { Uri.fromFile(File(it)) }
        if (isResumed && videoSource != null && !ringbackVideoError) {
            key(videoSource) {
                RingbackVideo(
                    uri = videoSource,
                    onError = { ringbackVideoError = true },
                    onStarted = { if (isOutgoing) onRingbackStarted(number) },
                )
            }
            if (isOutgoing) RingbackBadgeCanvas(badgeRule, modifier = Modifier.fillMaxSize().padding(top = 60.dp, bottom = 100.dp))
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
                        Text(displayName, color = activeRule.callerNameStyle.textColor(Color.White), fontSize = activeRule.callerNameStyle.fontSizeSp?.sp ?: 32.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        displayNumber?.let {
                            Text(it, color = activeRule.callerNumberStyle.textColor(Color.White), fontSize = activeRule.callerNumberStyle.fontSizeSp?.sp ?: 16.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(location, color = activeRule.locationTextStyle.textColor(Color(0x99FFFFFF)), fontSize = activeRule.locationTextStyle.fontSizeSp?.sp ?: 14.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier.padding(start = 16.dp).size(70.dp).clip(CircleShape).background(Color(0xFF535353)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "联系人头像", tint = Color.White, modifier = Modifier.size(46.dp))
                    }
                }
                Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val status = activeRule.name
                    if (activeRule.statusTextStyle.visible) Text(status, color = activeRule.statusTextStyle.textColor(Color.White), fontSize = activeRule.statusTextStyle.fontSizeSp?.sp ?: 18.sp, modifier = Modifier.offset(activeRule.statusTextStyle.offsetXdp.dp, activeRule.statusTextStyle.offsetYdp.dp).background(activeRule.statusTextStyle.backgroundColorArgb?.let { Color(it.toULong()) } ?: Color.Transparent, RoundedCornerShape(3.dp)))
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
                        val active = configuration.statuses.firstOrNull { it.stage == CallStage.ACTIVE }
                        if (active != null) transitionToStatus(active.id) else callState = CallSessionState.ACTIVE
                        val vibrator = context.getSystemService(Vibrator::class.java)
                        if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    }) { Text("接通", color = Color(0xFFB5E8B8)) }
                }
                if (callState == CallSessionState.ACTIVE) {
                    if (!microphoneGranted) {
                        TextButton(onClick = { permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) }) {
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
                InCallDtmfPad(digits = dtmfDigits, onDigit = { dtmfDigits += it }, onClose = { dialpadOpen = false },
                    toneEnabled = extras.dialpadToneEnabled)
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow) {
                                ruleFor(CallButtonId.RECORD).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "录音", painterResource(R.drawable.incall_btn_record_call), recording, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.RECORD) { recording = !recording } } }
                            } else {
                                ruleFor(CallButtonId.MUTE).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "静音", painterResource(R.drawable.incall_btn_mute_call), muted, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.MUTE) { muted = !muted } } }
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow) {
                                ruleFor(CallButtonId.HOLD).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "保持", painterResource(R.drawable.incall_btn_hold_call), callState == CallSessionState.ON_HOLD, enabled = rule.enabled, visualStyle = rule.visualStyle) {
                                    onConfiguredButton(CallButtonId.HOLD) {
                                        val targetStage = if (callState == CallSessionState.ACTIVE) CallStage.HOLD else CallStage.ACTIVE
                                        configuration.statuses.firstOrNull { it.stage == targetStage }?.let { transitionToStatus(it.id) }
                                    }
                                } }
                            } else {
                                ruleFor(CallButtonId.HOLD).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "暂停", painterResource(R.drawable.incall_btn_hold_call), false, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.HOLD) { playOutcome(CallOutcome.NO_ANSWER) } } }
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow) {
                                ruleFor(CallButtonId.ADD_CALL).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "添加通话", painterResource(R.drawable.incall_btn_add_call), false, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.ADD_CALL) { } } }
                            } else {
                                ruleFor(CallButtonId.VIDEO_CALL).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "视频", painterResource(R.drawable.incall_btn_change_to_video), false, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.VIDEO_CALL) { playOutcome(CallOutcome.BUSY) } } }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow) {
                                ruleFor(CallButtonId.MUTE).takeIf { it.visible }?.let { rule -> InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "静音", painterResource(R.drawable.incall_btn_mute_call), muted, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.MUTE) { muted = !muted } } }
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow) {
                                val rule = ruleFor(CallButtonId.VIDEO_CALL)
                                if (rule.visible) InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "视频通话", painterResource(R.drawable.incall_btn_change_to_video), false, enabled = rule.enabled, visualStyle = rule.visualStyle) { onConfiguredButton(CallButtonId.VIDEO_CALL) { } }
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (!outgoingRow && (callState == CallSessionState.ACTIVE || callState == CallSessionState.ON_HOLD)) {
                                val rule = ruleFor(CallButtonId.AI_CALL_ASSISTANT)
                                if (rule.visible) InCallAction(rule.label?.takeIf { it.isNotBlank() } ?: "AI 通话助手", painterResource(R.drawable.incall_btn_call_assistant), false, enabled = rule.enabled, visualStyle = rule.visualStyle, iconSizeDp = 68, tintIcon = false) { onConfiguredButton(CallButtonId.AI_CALL_ASSISTANT) { } }
                            }
                        }
                    }
                }
            }
            val speakerRule = ruleFor(CallButtonId.SPEAKER)
            val hangupRule = ruleFor(CallButtonId.HANGUP)
            val keypadRule = ruleFor(CallButtonId.KEYPAD)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (speakerRule.visible) InCallAction(
                        speakerRule.label?.takeIf { it.isNotBlank() } ?: "扬声器",
                        painterResource(R.drawable.incall_btn_audiomode_speaker),
                        speakerOn,
                        enabled = speakerRule.enabled,
                        visualStyle = speakerRule.visualStyle,
                    ) { onConfiguredButton(CallButtonId.SPEAKER) { speakerOn = !speakerOn } }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (hangupRule.visible) Column(
                        modifier = Modifier.offset(hangupRule.visualStyle.offsetXdp.dp, hangupRule.visualStyle.offsetYdp.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val enabled = hangupRule.enabled && canHangUp
                        IconButton(
                            onClick = { onConfiguredButton(CallButtonId.HANGUP, ::requestHangup) },
                            enabled = enabled,
                            modifier = Modifier.size(70.dp),
                        ) {
                            if (hangupRule.visualStyle.backgroundColorArgb == null && hangupRule.visualStyle.textColorArgb == null) {
                                Image(painterResource(R.drawable.incall_btn_reject), contentDescription = hangupRule.label?.takeIf { it.isNotBlank() } ?: "挂断", modifier = Modifier.size(68.dp), alpha = if (enabled) 1f else 0.4f)
                            } else {
                                Icon(Icons.Filled.CallEnd, contentDescription = hangupRule.label?.takeIf { it.isNotBlank() } ?: "挂断", tint = hangupRule.visualStyle.textColor(if (enabled) Color.White else Color(0x66FFFFFF)), modifier = Modifier.size(36.dp))
                            }
                        }
                        Text(hangupRule.label?.takeIf { it.isNotBlank() } ?: "挂断", color = hangupRule.visualStyle.textColor(if (enabled) Color.White else Color(0x66FFFFFF)), fontSize = hangupRule.visualStyle.fontSizeSp?.sp ?: 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (keypadRule.visible) InCallAction(
                        keypadRule.label?.takeIf { it.isNotBlank() } ?: "拨号盘",
                        painterResource(R.drawable.incall_btn_dialpad),
                        dialpadOpen,
                        enabled = keypadRule.enabled,
                        visualStyle = keypadRule.visualStyle,
                    ) {
                        onConfiguredButton(CallButtonId.KEYPAD) {
                            if (dialpadOpen) dialpadOpen = false else {
                                dtmfDigits = ""
                                dialpadOpen = true
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun RingbackVideo(uri: Uri, onError: () -> Unit, onStarted: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                    onStarted()
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
private fun InCallAction(label: String, icon: Painter?, selected: Boolean, enabled: Boolean, visualStyle: CallVisualStyle = CallVisualStyle(), iconSizeDp: Int = 36, tintIcon: Boolean = true, onClick: () -> Unit) {
    Column(modifier = Modifier.width(88.dp).offset(visualStyle.offsetXdp.dp, visualStyle.offsetYdp.dp).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        InCallButtonSurface(
            selected = enabled && selected,
            customColor = visualStyle.backgroundColorArgb?.let { Color(it.toULong()) },
        ) {
            if (icon != null) Image(icon, contentDescription = label, modifier = Modifier.size(iconSizeDp.dp), colorFilter = if (tintIcon) ColorFilter.tint(visualStyle.textColor(if (!enabled) Color(0x66FFFFFF) else if (selected) Color.Black else Color.White)) else null)
        }
        Text(label, color = visualStyle.textColor(if (enabled) Color.White else Color(0x66FFFFFF)), fontSize = visualStyle.fontSizeSp?.sp ?: 12.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
    }
}

private fun CallVisualStyle.textColor(fallback: Color): Color = textColorArgb?.let { Color(it.toULong()) } ?: fallback

@Composable
private fun InCallDtmfPad(digits: String, onDigit: (String) -> Unit, onClose: () -> Unit, toneEnabled: Boolean) {
    val playTone = rememberDialpadTone(toneEnabled)
    val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(digits, color = Color.White, fontSize = 20.sp, maxLines = 1, modifier = Modifier.height(28.dp))
        keys.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    val interactionSource = rememberDialpadToneInteractionSource(digit, playTone)
                    Column(modifier = Modifier.width(78.dp).height(54.dp).clickable(interactionSource = interactionSource) { onDigit(digit) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(digit, color = Color.White, fontSize = 28.sp, lineHeight = 30.sp)
                        Text(letters, color = Color(0xFFB8B8B8), fontSize = 10.sp)
                    }
                }
            }
        }
        Text("收起拨号键盘", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClose))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF25282D, widthDp = 540, heightDp = 1176)
@Composable
private fun InCallScreenPreview() {
    val statuses = remember {
        defaultCallStatuses().map { status ->
            if (status.id == "active") status.copy(durationSeconds = 0) else status
        }
    }
    OPPO_Call_ComposeTheme(darkTheme = true, dynamicColor = false) {
        InCallScreen(
            number = "19136066659",
            mode = CallAudioMode.DEFAULT_TONE,
            initialStatusId = "active",
            configuration = CallFlowConfiguration(initialStatusId = "active", statuses = statuses),
            mediaEntries = emptyList(),
            onCallFinished = {},
        )
    }
}
