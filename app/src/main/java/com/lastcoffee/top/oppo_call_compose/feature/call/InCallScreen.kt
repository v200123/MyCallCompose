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
import android.view.WindowManager
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallButtonId
import com.lastcoffee.top.oppo_call_compose.model.CallButtonRule
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallVisualStyle
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.BadgeRule
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallButtonSurface
import com.lastcoffee.top.oppo_call_compose.ui.common.InCallPreviewBackdrop
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadTone
import com.lastcoffee.top.oppo_call_compose.ui.common.rememberDialpadToneInteractionSource
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.model.defaultButtons
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
    simSlot: Int = 1,
    screenVariant: CallScreenVariant = CallScreenVariant.forNumber(number),
    resolvedContact: ContactMatch? = null,
    badgeRule: BadgeRule? = null,
    onRingbackStarted: (String) -> Unit = {},
    onCallFinished: suspend (ManualCall) -> Unit,
) {
    val executor = remember(number, configuration, initialStatusId) {
        CallRuleExecutor(configuration, initialStatusId, SystemClock.elapsedRealtime())
    }
    var executionStep by remember(executor) { mutableStateOf(executor.step) }
    val stepRevision = executionStep.revision
    val activeRule = executionStep.rule
    val activeStatusId = activeRule.id
    val callState = activeRule.stage.toSessionState()
    val sessionTiming = executor.timing
    val connectedAt = sessionTiming.connectedAtElapsedMillis
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var dialpadOpen by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    val callStartedAtMillis = remember(number) { System.currentTimeMillis() }
    val callStartedAtElapsed = remember(number) { SystemClock.elapsedRealtime() }
    val context = LocalContext.current
    var ringbackVideoError by remember { mutableStateOf(false) }
    var callOutcome by remember(number) { mutableStateOf(activeRule.outcome?.let { value -> CallOutcome.entries.firstOrNull { it.name == value } }) }
    var promptPlaying by remember(number) { mutableStateOf(false) }
    val isPreview = LocalInspectionMode.current
    val hangupPhrase = remember(context) { runCatching { voiceHangupPhrase(context) }.getOrDefault("语音口令") }
    var microphoneGranted by remember {
        mutableStateOf(isPreview || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceFailed by remember { mutableStateOf(false) }
    val permissionLauncher = if (isPreview) null else rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
    }
    val lifecycleOwner = context as? LifecycleOwner
    var isResumed by remember { mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    val contact = resolvedContact
    val configuredPromptType = extras.promptFor(number)
    val promptRuleId = configuredPromptType?.let { extras.promptRuleFor(it, screenVariant) }
    val promptTargetRule = promptRuleId?.let { selectedId ->
        executor.statuses.firstOrNull { it.id == selectedId && it.stage == CallStage.OUTCOME }
    } ?: executor.statuses.firstOrNull { it.id == configuredPromptType?.legacyStatusId && it.stage == CallStage.OUTCOME }
    val promptType = configuredPromptType
    val promptRuleActive = promptPlaying &&
        (activeStatusId == promptTargetRule?.id || (promptTargetRule == null && callState == CallSessionState.PLAYING_OUTCOME))
    val displayName = contact?.name ?: if (normalizePhoneNumber(number) == "10086") "中国移动" else number
    val displayNumber = contact?.number
    // An avatar belongs to the caller identity, not to the call's connection state.
    val hasCallerAvatar = screenVariant == CallScreenVariant.WITH_AVATAR
    val avatarBitmap = remember(extras.avatarPhotoFor(number)) { decodeAvatarFile(extras.avatarPhotoFor(number)) }
    val location = extras.locationFor(number) ?: resolvedLocation ?: contact?.location ?: "未知归属地"
    val mediaForMode = remember(mode, mediaEntries) { mediaEntries.filter { it.mode == mode && mode.acceptsMimeType(it.mimeType) && File(it.filePath).exists() } }
    val ringbackVideos = remember(mediaEntries) { mediaEntries.filter { it.mode == CallAudioMode.RINGBACK && it.mimeType.startsWith("video/") && File(it.filePath).isFile } }
    val randomRingbackId = remember(number, ringbackVideos) { ringbackVideos.randomOrNull()?.id }
    val currentMedia = remember(activeStatusId, activeRule.mediaEntryId, activeRule.playSoundOnce, mediaForMode, mediaEntries, extras, number, randomRingbackId, callState, promptPlaying) {
        val selectedMedia = mediaEntries.firstOrNull {
            it.id == activeRule.mediaEntryId && it.mode.isCallMode && it.mode.acceptsMimeType(it.mimeType) &&
                File(it.filePath).isFile && (!extras.blocksRingback(number) || !it.mimeType.startsWith("video/"))
        }
        if (activeRule.playSoundOnce || (promptType != null && (callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER))) null
        else if (promptRuleActive) mediaEntries.firstOrNull {
            it.id == activeRule.mediaEntryId && (it.mimeType.startsWith("audio/") || it.mimeType.startsWith("video/")) && File(it.filePath).isFile
        }
        else if (selectedMedia != null) selectedMedia
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
            mediaForMode.filter { !it.mimeType.startsWith("video/") }.randomOrNull()
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

    fun refreshExecution() {
        executionStep = executor.step
        callOutcome = executionStep.rule.outcome?.let { value -> CallOutcome.entries.firstOrNull { it.name == value } }
        elapsedSeconds = sessionTiming.elapsedSeconds(SystemClock.elapsedRealtime())
        if (executionStep.rule.stage in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)) {
            recording = false
            dialpadOpen = false
            dtmfDigits = ""
        }
    }

    fun transitionToStatus(id: String, expectedRevision: Long = stepRevision) {
        if (executor.moveTo(id, SystemClock.elapsedRealtime(), expectedRevision)) refreshExecution()
    }

    fun onStepAudioFinished(revision: Long) {
        if (executor.audioFinished(revision, SystemClock.elapsedRealtime())) refreshExecution()
    }

    fun onConfiguredButton(id: CallButtonId, defaultAction: () -> Unit) {
        if (!executor.isCurrent(stepRevision)) return
        val rule = activeRule.buttons.firstOrNull { it.id == id }
            ?: defaultButtons(activeRule.stage).first { it.id == id }
        if (!rule.enabled) return
        val targetId = rule.targetStatusId
        if (targetId != null) transitionToStatus(targetId) else defaultAction()
    }

    fun unavailableAction(label: String) {
        Toast.makeText(context, "$label 暂不可用", Toast.LENGTH_SHORT).show()
    }

    fun requestHangup(expectedRevision: Long = stepRevision) {
        if (executor.hangup(SystemClock.elapsedRealtime(), expectedRevision)) refreshExecution()
    }

    BackHandler {
        if (dialpadOpen) dialpadOpen = false else requestHangup()
    }

    val autoHangUpSeconds = extras.autoHangUpSecondsFor(number)
    LaunchedEffect(number, connectedAt, autoHangUpSeconds, stepRevision) {
        val connectedAtMillis = connectedAt ?: return@LaunchedEffect
        val delaySeconds = autoHangUpSeconds ?: return@LaunchedEffect
        if (callState != CallSessionState.ACTIVE && callState != CallSessionState.ON_HOLD) return@LaunchedEffect
        val remainingMillis = (connectedAtMillis + delaySeconds * 1_000L - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        delay(remainingMillis)
        requestHangup()
    }

    LaunchedEffect(number, promptType, promptRuleId, stepRevision, promptPlaying) {
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
        if (target != null) transitionToStatus(target.id) else {
            executor.statuses.firstOrNull { it.stage == CallStage.OUTCOME }?.let { transitionToStatus(it.id) }
        }
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

    DisposableEffect(stepRevision, microphoneGranted, isResumed) {
        if (!isPreview && callState == CallSessionState.ACTIVE && microphoneGranted && isResumed) {
            voiceFailed = false
            val detector = VoiceHangupDetector(
                context = context.applicationContext,
                onListening = { voiceListening = true },
                onKeyword = { requestHangup(stepRevision) },
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

    DisposableEffect(stepRevision, callOutcome, currentMedia?.filePath, promptRuleActive, isResumed) {
        val outcome = callOutcome
        if (callState == CallSessionState.PLAYING_OUTCOME && outcome != null && currentMedia == null && isResumed && !activeRule.playSoundOnce && !promptRuleActive) {
            val player = CallOutcomePlayer(
                context = context,
                outcome = outcome,
                onCycle = {},
                onFinished = { onStepAudioFinished(stepRevision) },
            )
            onDispose { player.stop() }
        } else {
            onDispose { }
        }
    }

    DisposableEffect(stepRevision, promptRuleActive, promptType, promptUsesRuleAudio, isResumed) {
        if (callState == CallSessionState.PLAYING_OUTCOME && promptRuleActive && promptType != null && !promptUsesRuleAudio && isResumed) {
            val rawId = when (promptType) {
                CallPromptType.POWER_OFF -> R.raw.audio_power_off
                CallPromptType.EMPTY_NUMBER -> R.raw.audio_empty_number
                CallPromptType.BUSY -> R.raw.audio_user_busy
                CallPromptType.UNREACHABLE -> R.raw.audio_busy
            }
            val player = MediaPlayer.create(context, rawId)
            if (player == null) {
                onDispose { }
            } else {
                player.setOnCompletionListener {
                    onStepAudioFinished(stepRevision)
                }
                player.setOnErrorListener { _, _, _ -> true }
                runCatching { player.start() }
                onDispose {
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    runCatching { if (player.isPlaying) player.stop() }
                    player.release()
                }
            }
        } else onDispose { }
    }

    // This entry owns the timer even while the screen is paused. Changing entries
    // cancels it, and the revision check also rejects callbacks already queued.
    LaunchedEffect(stepRevision) {
        val revision = stepRevision
        while (executor.isCurrent(revision)) {
            val remaining = executor.remainingMillis(SystemClock.elapsedRealtime()) ?: return@LaunchedEffect
            if (remaining > 0) {
                delay(remaining)
                // A scheduler wakeup does not guarantee that the monotonic
                // deadline has passed. Recheck instead of losing this entry.
                continue
            }
            val now = SystemClock.elapsedRealtime()
            if (executor.claimCompletion(revision, now)) {
                onCallFinished(ManualCall(number = number, kind = CallKind.OUTGOING,
                    durationSeconds = sessionTiming.durationSeconds(now), timeMillis = callStartedAtMillis,
                    wasConnected = sessionTiming.connectedAtElapsedMillis != null))
            } else if (executor.advanceIfDue(revision, now)) refreshExecution()
            return@LaunchedEffect
        }
    }

    LaunchedEffect(stepRevision, currentMedia?.filePath, activeRule.audioPlayCount, isResumed) {
        val media = currentMedia
        if (media != null && media.mimeType.startsWith("audio/") && isResumed) {
            val player = MediaPlayer()
            val playCount = activeRule.audioPlayCount.coerceIn(0, 99)
            var completedPlays = 0
            runCatching {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC)
                player.setDataSource(media.filePath)
                player.isLooping = playCount == 0
                if (playCount > 0) player.setOnCompletionListener {
                    completedPlays++
                    if (completedPlays < playCount) {
                        it.seekTo(0)
                        it.start()
                    } else onStepAudioFinished(stepRevision)
                }
                player.setOnPreparedListener { it.start() }
                player.prepareAsync()
            }.onFailure { player.release() }
            try {
                if (activeRule.durationSeconds > 0) delay(activeRule.durationSeconds * 1_000L) else kotlinx.coroutines.awaitCancellation()
            } finally {
                runCatching { player.setOnCompletionListener(null); player.setOnPreparedListener(null) }
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
        }
    }

    DisposableEffect(stepRevision, isResumed) {
        onDispose {
            statusSoundPlayers.toList().forEach { player ->
                runCatching { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.setOnPreparedListener(null) }
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
            statusSoundPlayers.clear()
            playingStatusSoundCount = 0
        }
    }

    LaunchedEffect(stepRevision, currentSound?.filePath, isResumed) {
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
            player.setOnCompletionListener { finish(); onStepAudioFinished(stepRevision) }
            player.setOnErrorListener { _, _, _ -> finish(); true }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        }.onFailure { finish() }
    }

    LaunchedEffect(connectedAt, callState) {
        val start = connectedAt
        if (start != null && (callState == CallSessionState.ACTIVE || callState == CallSessionState.ON_HOLD)) {
            while (true) {
                elapsedSeconds = sessionTiming.elapsedSeconds(SystemClock.elapsedRealtime())
                delay(250)
            }
        }
    }

    val isOutgoing = callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER
    val canHangUp = callState != CallSessionState.DISCONNECTING && callState != CallSessionState.DISCONNECTED
    fun ruleFor(id: CallButtonId) = activeRule.buttons.firstOrNull { it.id == id }
        ?: defaultButtons(activeRule.stage).first { it.id == id }
    val outgoingRow = callState == CallSessionState.DIALING || callState == CallSessionState.WAITING_FOR_ANSWER

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().drawWithContent {
        // The call Activity switches from an opaque page to a transparent
        // wallpaper window. Clear the previous frame before drawing controls.
        drawRect(Color.Transparent, blendMode = BlendMode.Clear)
        drawContent()
    }) {
      Box(modifier = Modifier.fillMaxSize()) {
        InCallPreviewBackdrop()
        val videoSource = currentMedia?.takeIf { it.mimeType.startsWith("video/") && (isOutgoing || promptRuleActive) }?.filePath?.let { Uri.fromFile(File(it)) }
        if (isResumed && videoSource != null && !ringbackVideoError) {
            key(stepRevision, videoSource) {
                RingbackVideo(
                    uri = videoSource,
                    onError = { if (executor.isCurrent(stepRevision)) ringbackVideoError = true },
                    onStarted = { if (executor.isCurrent(stepRevision) && isOutgoing) onRingbackStarted(number) },
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
                modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, top = if (dialpadOpen) 24.dp else 72.dp),
                horizontalAlignment = if (!hasCallerAvatar) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (!hasCallerAvatar) {
                    Text(
                        formatOutgoingNumber(displayName),
                        color = activeRule.callerNameStyle.textColor(Color.White),
                        fontSize = activeRule.callerNameStyle.fontSizeSp?.sp ?: 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (isOutgoing) location else displayNumber ?: location,
                        color = activeRule.locationTextStyle.textColor(Color.White),
                        fontSize = activeRule.locationTextStyle.fontSizeSp?.sp ?: 16.sp,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, color = activeRule.callerNameStyle.textColor(Color.White), fontSize = activeRule.callerNameStyle.fontSizeSp?.sp ?: 36.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(displayNumber ?: if (number == "10086") "10086 服务号码" else location,
                                color = activeRule.callerNumberStyle.textColor(Color.White), fontSize = activeRule.callerNumberStyle.fontSizeSp?.sp ?: 18.sp,
                                modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(modifier = Modifier.padding(start = 12.dp).size(70.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            if (avatarBitmap != null) Image(avatarBitmap.asImageBitmap(), contentDescription = "联系人头像",
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Filled.Person, contentDescription = "联系人头像", tint = Color(0xFF535353), modifier = Modifier.size(46.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = if (!hasCallerAvatar) 25.dp else 23.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (!hasCallerAvatar) Arrangement.Center else Arrangement.Start,
                ) {
                    SimBadge(simSlot)
                    val status = activeRule.name
                    Text(status, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    if (connectedAt != null && callState != CallSessionState.DISCONNECTING) {
                        Text("%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = Color.White, fontSize = 20.sp,
                            modifier = Modifier.padding(start = 7.dp))
                    }
                }
                if (isOutgoing && ringbackVideoError) {
                    Text("彩铃视频无法播放，请重新选择", color = Color(0xFFFFC9C9), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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
                InCallDtmfPad(digits = dtmfDigits, onDigit = { dtmfDigits = (dtmfDigits + it).take(256) },
                    toneEnabled = extras.dialpadToneEnabled)
            } else {
                val buttonRows = listOf(
                    listOf(CallButtonId.RECORD, CallButtonId.HOLD, CallButtonId.ADD_CALL),
                    listOf(CallButtonId.MUTE, CallButtonId.VIDEO_CALL, CallButtonId.AI_CALL_ASSISTANT),
                    listOf(CallButtonId.SPEAKER, CallButtonId.HANGUP, CallButtonId.KEYPAD),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 84.dp)) {
                    buttonRows.forEachIndexed { rowIndex, ids ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ids.forEach { id ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    val rule = ruleFor(id)
                                    if (id == CallButtonId.HANGUP) {
                                            InCallHangup(rule, rule.enabled && canHangUp) {
                                                onConfiguredButton(CallButtonId.HANGUP) { requestHangup() }
                                            }
                                    } else {
                                            val selected = when (id) {
                                                CallButtonId.RECORD -> recording
                                                CallButtonId.HOLD -> callState == CallSessionState.ON_HOLD
                                                CallButtonId.MUTE -> muted
                                                CallButtonId.SPEAKER -> speakerOn
                                                CallButtonId.KEYPAD -> dialpadOpen
                                                else -> false
                                            }
                                            val iconId = when (id) {
                                                CallButtonId.RECORD -> if (recording) R.drawable.incall_btn_record_call_selected else R.drawable.incall_btn_record_call
                                                CallButtonId.HOLD -> if (selected) R.drawable.incall_btn_hold_call_selected else R.drawable.incall_btn_hold_call
                                                CallButtonId.ADD_CALL -> R.drawable.incall_btn_add_call
                                                CallButtonId.MUTE -> if (muted) R.drawable.incall_btn_mute_call_selected else R.drawable.incall_btn_mute_call
                                                CallButtonId.VIDEO_CALL -> R.drawable.incall_btn_change_to_video
                                                CallButtonId.AI_CALL_ASSISTANT -> R.drawable.incall_btn_call_assistant
                                                CallButtonId.SPEAKER -> if (speakerOn) R.drawable.incall_btn_audiomode_speaker_selected else R.drawable.incall_btn_audiomode_speaker
                                                else -> if (dialpadOpen) R.drawable.incall_btn_dialpad_selected else R.drawable.incall_btn_dialpad
                                            }
                                            InCallAction(
                                                label = rule.label?.takeIf { it.isNotBlank() } ?: id.title,
                                                icon = painterResource(iconId),
                                                selected = selected,
                                                enabled = rule.enabled,
                                                visualStyle = rule.visualStyle,
                                                iconSizeDp = if (id == CallButtonId.AI_CALL_ASSISTANT) 66 else 36,
                                                tintIcon = id != CallButtonId.AI_CALL_ASSISTANT,
                                            ) {
                                                onConfiguredButton(id) {
                                                    when (id) {
                                                        CallButtonId.RECORD -> recording = !recording
                                                        CallButtonId.HOLD -> {
                                                            val targetStage = if (callState == CallSessionState.ON_HOLD) CallStage.ACTIVE else CallStage.HOLD
                                                            executor.statuses.firstOrNull { it.stage == targetStage }?.let { transitionToStatus(it.id) }
                                                        }
                                                        CallButtonId.MUTE -> muted = !muted
                                                        CallButtonId.SPEAKER -> speakerOn = !speakerOn
                                                        CallButtonId.KEYPAD -> dialpadOpen = !dialpadOpen
                                                        else -> unavailableAction(id.title)
                                                    }
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        if (rowIndex < buttonRows.lastIndex) Spacer(modifier = Modifier.height(22.dp))
                    }
                }
            }
        }
      }
    }
}

private fun formatOutgoingNumber(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length == 11 && value.all { it.isDigit() || it == ' ' || it == '-' }) {
        "${digits.take(3)} ${digits.substring(3, 7)} ${digits.takeLast(4)}"
    } else value
}

@Composable
private fun ChinaMobileLogo() {
    Canvas(modifier = Modifier.size(62.dp)) {
        val w = size.width
        val h = size.height
        fun wave(points: FloatArray, color: Color, width: Float) {
            val path = Path().apply {
                moveTo(points[0] * w, points[1] * h)
                cubicTo(points[2] * w, points[3] * h, points[4] * w, points[5] * h, points[6] * w, points[7] * h)
            }
            drawPath(path, color, style = Stroke(width = width * w, cap = StrokeCap.Round))
        }
        val blue = Color(0xFF3186C5)
        wave(floatArrayOf(.11f,.45f,.40f,.13f,.62f,.14f,.90f,.43f), blue, .052f)
        wave(floatArrayOf(.17f,.51f,.41f,.25f,.60f,.25f,.84f,.49f), blue, .055f)
        wave(floatArrayOf(.15f,.55f,.37f,.78f,.47f,.78f,.69f,.52f), Color(0xFF91BF41), .072f)
        wave(floatArrayOf(.11f,.60f,.36f,.89f,.61f,.89f,.88f,.58f), blue, .048f)
        wave(floatArrayOf(.16f,.66f,.39f,.90f,.60f,.81f,.83f,.61f), blue, .036f)
    }
}

@Composable
private fun SimBadge(slot: Int) {
    Box(
        modifier = Modifier.size(width = 12.dp, height = 15.dp)
            .background(Color.White, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(slot.toString(), color = Color(0xFF102018), fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
    }
}

@Composable
private fun InCallHangup(rule: CallButtonRule, enabled: Boolean, onClick: () -> Unit) {
    val label = rule.label?.takeIf { it.isNotBlank() } ?: "挂断"
    Column(
        modifier = Modifier.width(88.dp).offset(rule.visualStyle.offsetXdp.dp, rule.visualStyle.offsetYdp.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            if (rule.visualStyle.backgroundColorArgb == null && rule.visualStyle.textColorArgb == null) {
                Image(painterResource(R.drawable.incall_btn_reject), contentDescription = label,
                    modifier = Modifier.size(68.dp), alpha = if (enabled) 1f else 0.4f)
            } else {
                Box(modifier = Modifier.size(66.dp).background(
                    rule.visualStyle.backgroundColorArgb?.let { Color(it.toULong()) } ?: Color(0xFFE8424D), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CallEnd, contentDescription = label,
                        tint = rule.visualStyle.textColor(if (enabled) Color.White else Color(0x66FFFFFF)), modifier = Modifier.size(36.dp))
                }
            }
        }
        Text(label, color = rule.visualStyle.textColor(if (enabled) Color.White else Color(0x4DFFFFFF)),
            fontSize = rule.visualStyle.fontSizeSp?.sp ?: 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(if (pressed) 50 else 150), label = "call button press")
    Column(modifier = Modifier.width(88.dp).offset(visualStyle.offsetXdp.dp, visualStyle.offsetYdp.dp)
        .semantics { this.selected = selected }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = interactionSource, indication = null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        InCallButtonSurface(
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            selected = enabled && selected,
            customColor = visualStyle.backgroundColorArgb?.let { Color(it.toULong()) },
        ) {
            if (icon != null) Image(icon, contentDescription = label, modifier = Modifier.size(iconSizeDp.dp), alpha = if (enabled) 1f else 0.3f, colorFilter = if (tintIcon) ColorFilter.tint(visualStyle.textColor(if (selected && enabled) Color.Black else Color.White)) else null)
        }
        Text(label, color = visualStyle.textColor(if (enabled) Color.White else Color(0x4DFFFFFF)), fontSize = visualStyle.fontSizeSp?.sp ?: 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
    }
}

private fun CallVisualStyle.textColor(fallback: Color): Color = textColorArgb?.let { Color(it.toULong()) } ?: fallback

@Composable
private fun InCallDtmfPad(digits: String, onDigit: (String) -> Unit, toneEnabled: Boolean) {
    val playTone = rememberDialpadTone(toneEnabled)
    val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(digits, color = Color.White, fontSize = 40.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(19.dp))
        keys.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                row.forEach { (digit, letters) ->
                    val interactionSource = rememberDialpadToneInteractionSource(digit, playTone)
                    val pressed by interactionSource.collectIsPressedAsState()
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        InCallButtonSurface(modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, role = Role.Button) { onDigit(digit) }, selected = pressed) {
                            val foreground = if (pressed) Color.Black else Color.White
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(digit, color = foreground, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
                                if (letters.isNotEmpty()) Text(letters, color = foreground, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
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
            number = "10086",
            mode = CallAudioMode.DEFAULT_TONE,
            initialStatusId = "active",
            configuration = CallFlowConfiguration(initialStatusId = "active", statuses = statuses),
            mediaEntries = emptyList(),
            onCallFinished = {},
        )
    }
}
