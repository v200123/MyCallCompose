package com.lastcoffee.top.oppo_call_compose.feature.call

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

internal enum class CallOutcome(val status: String, val spokenPrompt: String) {
    NO_ANSWER("无人接听", "您拨打的电话暂时无人接听"),
    BUSY("对方通话中", ""),
    IN_CALL("正在通话中", "您拨打的电话正在通话中，请稍后再拨"),
    EMPTY_NUMBER("对方为空号", "您拨打的号码是空号"),
    POWERED_OFF("对方已关机", "您拨打的号码已关机"),
    UNREACHABLE("无法接通", "您拨打的电话暂时无法接通"),
}

/** Plays the local outcome sound and reports completion. It never starts a real phone call. */
internal class CallOutcomePlayer(
    context: Context,
    private val outcome: CallOutcome,
    scope: CoroutineScope,
    private val onCycle: (Int) -> Unit,
    private val onFinished: () -> Unit,
) {
    private var tone: ToneGenerator? = null
    private var speech: TextToSpeech? = null

    private val playbackJob = scope.launch(Dispatchers.Main) {
        try {
            if (outcome == CallOutcome.BUSY) {
                playTone(ToneGenerator.TONE_SUP_BUSY, 5_000, 1)
            } else {
                val cycles = if (outcome == CallOutcome.NO_ANSWER) 2 else 1
                // An engine that never completes must still let the call finish.
                val spoken = withTimeoutOrNull((if (cycles == 2) 20_000L else 10_000L).milliseconds) {
                    try {
                        playSpeech(context.applicationContext, cycles)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                }
                if (spoken == false) {
                    stopSpeech()
                    playTone(ToneGenerator.TONE_SUP_RINGTONE, 2_000 * cycles, cycles)
                }
            }
        } finally {
            release()
        }
        ensureActive()
        onFinished()
    }

    private suspend fun playSpeech(context: Context, cycles: Int): Boolean = coroutineScope {
        val initialized = CompletableDeferred<Int>()
        val completed = CompletableDeferred<Boolean>()
        val engine = TextToSpeech(context) { initialized.complete(it) }
        speech = engine
        if (initialized.await() != TextToSpeech.SUCCESS) return@coroutineScope false
        engine.setAudioAttributes(callAudioAttributes)
        val language = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            return@coroutineScope false
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                launch { onCycle(utteranceId.substringAfterLast('-').toIntOrNull() ?: 1) }
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId.endsWith("-$cycles")) completed.complete(true)
            }

            override fun onError(utteranceId: String) {
                completed.complete(false)
            }
        })
        repeat(cycles) { index ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(outcome.spokenPrompt, queueMode, Bundle(), "call-outcome-${index + 1}") == TextToSpeech.ERROR) {
                return@coroutineScope false
            }
        }
        completed.await()
    }

    private suspend fun playTone(toneId: Int, durationMs: Int, cycles: Int) {
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80) }.getOrNull()
        tone = generator
        if (generator == null) {
            delay(durationMs.toLong())
            return
        }
        val cycleLength = durationMs / cycles
        repeat(cycles) { index ->
            onCycle(index + 1)
            currentCoroutineContext().ensureActive()
            generator.startTone(toneId, cycleLength)
            delay(cycleLength.toLong())
        }
    }

    private fun stopSpeech() {
        val engine = speech ?: return
        speech = null
        runCatching { engine.setOnUtteranceProgressListener(null) }
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
    }

    private fun release() {
        val generator = tone
        tone = null
        runCatching { generator?.stopTone() }
        runCatching { generator?.release() }
        stopSpeech()
    }

    fun stop() {
        playbackJob.cancel()
        release()
    }
}
