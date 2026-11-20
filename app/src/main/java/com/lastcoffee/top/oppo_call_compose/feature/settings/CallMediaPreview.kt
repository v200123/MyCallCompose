package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.feature.call.promptRinging
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import java.io.File
import kotlinx.coroutines.delay

internal data class CallMediaPreview(
    val entry: CallMediaEntry,
    val playCount: Int = 1,
    val durationSeconds: Int? = null,
    val leadingMedia: CallMediaPreview? = null,
)

internal fun CallStatusRule.previewMedia(entry: CallMediaEntry): CallMediaPreview {
    val playCount = if (playSoundOnce) 1 else audioPlayCount
    val timed = durationSeconds > 0 || stage == CallStage.DISCONNECTING || stage == CallStage.DISCONNECTED
    return CallMediaPreview(entry, playCount.coerceIn(0, 99), durationSeconds.takeIf { timed })
}

internal fun rulePreviewEntries(
    rule: CallStatusRule,
    mediaEntries: List<CallMediaEntry>,
    extras: PhoneExtras,
    mode: CallAudioMode = CallAudioMode.DEFAULT_TONE,
    waitingRule: CallStatusRule? = null,
): List<CallMediaPreview> {
    if (rule.playSoundOnce && rule.soundMuted) return emptyList()
    val selected = mediaEntries.firstOrNull { entry ->
        if (rule.playSoundOnce) {
            entry.id == rule.soundEntryId && entry.mimeType.startsWith("audio/")
        } else {
            entry.id == rule.mediaEntryId && entry.mode.acceptsMimeType(entry.mimeType) &&
                entry.mode != CallAudioMode.DIALPAD_TONE
        }
    }
    if (rule.stage == CallStage.WAITING && !rule.playSoundOnce) {
        if (selected != null) return listOf(rule.previewMedia(selected))
        val valid = mediaEntries.filter { it.mode.acceptsMimeType(it.mimeType) }
        val normalTone = valid.firstOrNull { it.mode == CallAudioMode.DEFAULT_TONE }
        val candidates = listOfNotNull(normalTone) + valid.filter {
            it.mode == CallAudioMode.MUSIC || it.mode == CallAudioMode.RINGBACK
        }
        return candidates.map { rule.previewMedia(it) }
    }
    val previews = if (selected != null) listOf(rule.previewMedia(selected))
    else CallPromptType.entries.filter { extras.promptRuleFor(it) == rule.id }.mapNotNull { type ->
        mediaEntries.firstOrNull { it.id == type.defaultMediaId }?.let { rule.previewMedia(it) }
    }
    val type = CallPromptType.entries.firstOrNull { it.legacyStatusId == rule.id }
        ?: CallPromptType.entries.firstOrNull { extras.promptRuleFor(it) == rule.id }
        ?: return previews
    val available = mediaEntries.filter { it.mode.acceptsMimeType(it.mimeType) && File(it.filePath).isFile }
    val leadingEntry = available.firstOrNull { it.id == waitingRule?.mediaEntryId && it.mode.isCallMode }
        ?: available.firstOrNull { it.mode == mode && it.id == extras.defaultRingbackId }
        ?: available.firstOrNull { it.mode == mode }
        ?: available.firstOrNull { it.mode == CallAudioMode.DEFAULT_TONE }
        ?: return previews
    val plan = promptRinging(type, leadingEntry.mode)
    if (plan.skipWaiting) return previews
    val leadingMedia = CallMediaPreview(
        leadingEntry, plan.playCount, plan.durationSeconds.takeIf { it > 0 },
    )
    return previews.map { it.copy(leadingMedia = leadingMedia) }
}

@Composable
internal fun CallMediaPreviewButton(entries: List<CallMediaPreview>, enabled: Boolean = true) {
    if (entries.isEmpty()) return
    var choosing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CallMediaPreview?>(null) }
    Box {
        TextButton(
            enabled = enabled,
            onClick = { if (entries.size == 1) preview = entries.single() else choosing = true },
        ) { Text(if (entries.size == 1) entries.single().entry.previewAction else "选择试听或试看") }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.entry.previewAction} · ${option.entry.title}") },
                    onClick = { choosing = false; preview = option },
                )
            }
        }
    }
    preview?.let { CallMediaPreviewDialog(it, onClose = { preview = null }) }
}

private val CallMediaEntry.isVideo: Boolean get() = mimeType.startsWith("video/")
private val CallMediaEntry.previewAction: String get() = if (isVideo) "试看" else "试听"

private val previewAudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

@Composable
private fun CallMediaPreviewDialog(preview: CallMediaPreview, onClose: () -> Unit) {
    val clips = remember(preview) { listOfNotNull(preview.leadingMedia, preview) }
    var position by remember(preview) { mutableIntStateOf(0) }
    var replay by remember(preview) { mutableIntStateOf(0) }
    key(position, replay) {
        CallMediaClipDialog(
            preview = clips[position],
            title = preview.entry.title,
            onComplete = { if (position < clips.lastIndex) position++ },
            onReplay = { position = 0; replay++ },
            onClose = onClose,
        )
    }
}

@Composable
private fun CallMediaClipDialog(
    preview: CallMediaPreview,
    title: String,
    onComplete: () -> Unit,
    onReplay: () -> Unit,
    onClose: () -> Unit,
) {
    val entry = preview.entry
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var running by remember(preview) { mutableStateOf(true) }
    var mediaFinished by remember(preview) { mutableStateOf(false) }
    var loading by remember(preview) { mutableStateOf(true) }
    var failed by remember(preview) { mutableStateOf(false) }
    val playing = running && !mediaFinished && preview.durationSeconds != 0

    fun fail() {
        failed = true
        running = false
    }

    fun complete() {
        if (running) {
            running = false
            onComplete()
        }
    }

    fun finishMedia() {
        mediaFinished = true
        if (preview.durationSeconds == null) complete()
    }

    LaunchedEffect(preview, running) {
        if (running && preview.durationSeconds != null) {
            delay(preview.durationSeconds * 1_000L)
            complete()
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) running = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${entry.previewAction} · $title") },
        text = {
            Column {
                if (entry.title != title) Text(entry.title)
                if (entry.isVideo) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp).background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playing) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    VideoView(context).apply {
                                        var completedPlays = 0
                                        setAudioAttributes(previewAudioAttributes)
                                        setOnPreparedListener { player ->
                                            player.isLooping = preview.playCount == 0
                                            runCatching { start() }.onSuccess { loading = false }.onFailure { fail() }
                                        }
                                        if (preview.playCount > 0) setOnCompletionListener {
                                            completedPlays++
                                            if (completedPlays >= preview.playCount) finishMedia()
                                            else runCatching { seekTo(0); start() }.onFailure { fail() }
                                        }
                                        setOnErrorListener { _, _, _ -> fail(); true }
                                        runCatching { setVideoURI(Uri.fromFile(File(entry.filePath))) }.onFailure { fail() }
                                    }
                                },
                                onRelease = {
                                    it.setOnPreparedListener(null)
                                    it.setOnCompletionListener(null)
                                    it.setOnErrorListener(null)
                                    it.stopPlayback()
                                },
                            )
                        }
                    }
                } else if (playing) {
                    DisposableEffect(preview) {
                        val player = MediaPlayer()
                        var completedPlays = 0
                        runCatching {
                            player.setAudioAttributes(previewAudioAttributes)
                            player.setDataSource(entry.filePath)
                            player.isLooping = preview.playCount == 0
                            player.setOnPreparedListener {
                                runCatching { it.start() }.onSuccess { loading = false }.onFailure { fail() }
                            }
                            if (preview.playCount > 0) player.setOnCompletionListener {
                                completedPlays++
                                if (completedPlays >= preview.playCount) finishMedia()
                                else runCatching { it.seekTo(0); it.start() }.onFailure { fail() }
                            }
                            player.setOnErrorListener { _, _, _ -> fail(); true }
                            player.prepareAsync()
                        }.onFailure { fail() }
                        onDispose {
                            player.setOnPreparedListener(null)
                            player.setOnCompletionListener(null)
                            player.setOnErrorListener(null)
                            player.release()
                        }
                    }
                }
                Text(
                    text = when {
                        failed -> "文件无法播放"
                        !running -> "播放已结束"
                        mediaFinished -> "音频已播完，等待状态结束"
                        loading -> "正在加载…"
                        else -> "正在${entry.previewAction}"
                    },
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
        dismissButton = {
            if (!running) {
                TextButton(onClick = onReplay) { Text("重播") }
            }
        },
    )
}
