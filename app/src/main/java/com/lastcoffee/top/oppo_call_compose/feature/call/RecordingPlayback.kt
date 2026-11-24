package com.lastcoffee.top.oppo_call_compose.feature.call

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale

/** One player per expanded recording; leaving the page releases its audio. */
internal class RecordingPlayback {
    var duration by mutableIntStateOf(0)
        private set
    var position by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var player by mutableStateOf<MediaPlayer?>(null)
    val ready: Boolean get() = player != null

    fun prepared(media: MediaPlayer, autoPlay: Boolean) {
        player = media
        duration = media.duration.coerceAtLeast(0)
        if (autoPlay) toggle()
    }

    fun toggle() {
        val media = player ?: return
        runCatching { if (playing) media.pause() else media.start() }
            .onSuccess { playing = !playing }
            .onFailure { fail() }
    }

    fun pause() {
        if (playing) toggle()
    }

    fun seekTo(target: Int) {
        val value = target.coerceIn(0, duration)
        runCatching { player?.seekTo(value) }.onSuccess { position = value }.onFailure { fail() }
    }

    fun completed() {
        playing = false
        seekTo(0)
    }

    fun updatePosition() {
        runCatching { player?.currentPosition }.onSuccess { position = it ?: 0 }.onFailure { fail() }
    }

    fun fail() {
        player = null
        playing = false
        failed = true
    }

    fun detached() {
        player = null
        playing = false
    }
}

@Composable
internal fun rememberRecordingPlayback(audio: Uri, autoPlay: Boolean = false): RecordingPlayback {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(audio) { RecordingPlayback() }
    DisposableEffect(context, audio, lifecycle) {
        val media = MediaPlayer()
        try {
            media.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(context, audio)
            media.setOnPreparedListener {
                state.prepared(it, autoPlay && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
            media.setOnCompletionListener { state.completed() }
            media.setOnErrorListener { _, _, _ -> state.fail(); true }
            media.prepareAsync()
        } catch (_: Exception) {
            state.fail()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.detached()
            media.setOnPreparedListener(null)
            media.setOnCompletionListener(null)
            media.setOnErrorListener(null)
            media.release()
        }
    }
    LaunchedEffect(state, state.playing) {
        while (state.playing) {
            state.updatePosition()
            delay(150)
        }
    }
    return state
}

internal fun formatRecordingTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
