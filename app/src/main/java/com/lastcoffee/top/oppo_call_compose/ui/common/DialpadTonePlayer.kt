package com.lastcoffee.top.oppo_call_compose.ui.common

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.lastcoffee.top.oppo_call_compose.BuildConfig
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Same stream, volume and duration as bohao's TonePlayer: local DTMF feedback only. */
internal class DialpadTonePlayer(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var generator: ToneGenerator? = null

    fun play(key: String) {
        val tone = when (key) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audioManager.getStreamVolume(AudioManager.STREAM_DTMF) == 0) {
            if (BuildConfig.DEBUG) Log.d("DialpadTone", "blocked by ringer mode or DTMF volume")
            return
        }
        runCatching {
            val current = generator ?: ToneGenerator(AudioManager.STREAM_DTMF, 80).also { generator = it }
            current.stopTone()
            val started = current.startTone(tone, 150)
            if (BuildConfig.DEBUG) Log.d("DialpadTone", "startTone=$started")
            if (!started) release()
        }.onFailure { error -> Log.w("DialpadTone", "Unable to play keypad tone", error); release() }
    }

    fun release() {
        val current = generator
        generator = null
        runCatching { current?.stopTone() }
        runCatching { current?.release() }
    }
}

@Composable
internal fun rememberDialpadTone(enabled: Boolean): (String) -> Unit {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(context, preview) { if (preview) null else DialpadTonePlayer(context) }
    DisposableEffect(player, lifecycleOwner, enabled) {
        if (!enabled) player?.release()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.release()
        }
    }
    return remember(player, enabled, lifecycleOwner) {
        { key ->
            if (BuildConfig.DEBUG) Log.d("DialpadTone", "key press: enabled=$enabled lifecycle=${lifecycleOwner.lifecycle.currentState}")
            if (enabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player?.play(key)
        }
    }
}

/** Play once on touch down, including long presses, without changing key input handling. */
@Composable
internal fun rememberDialpadToneInteractionSource(key: String, playTone: (String) -> Unit): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    val currentPlayTone by rememberUpdatedState(playTone)
    LaunchedEffect(source, key) {
        source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) currentPlayTone(key)
        }
    }
    return source
}
