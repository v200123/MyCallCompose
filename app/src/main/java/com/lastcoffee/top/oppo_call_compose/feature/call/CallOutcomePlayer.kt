package com.lastcoffee.top.oppo_call_compose.feature.call

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal enum class CallOutcome(val status: String, val spokenPrompt: String) {
    NO_ANSWER("无人接听", "您拨打的电话暂时无人接听"),
    BUSY("对方通话中", ""),
    EMPTY_NUMBER("对方为空号", "您拨打的号码是空号"),
    POWERED_OFF("对方已关机", "您拨打的号码已关机"),
}

/** Plays the local outcome sound and reports completion. It never starts a real phone call. */
internal class CallOutcomePlayer(
    context: Context,
    private val outcome: CallOutcome,
    private val onCycle: (Int) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var fallbackStarted = false
    private var tone: ToneGenerator? = null
    private var speech: TextToSpeech? = null
    private val timeout = Runnable { finish() }

    init {
        if (outcome == CallOutcome.BUSY) {
            playTone(ToneGenerator.TONE_SUP_BUSY, 5_000, 1)
        } else {
            val cycles = if (outcome == CallOutcome.NO_ANSWER) 2 else 1
            // An engine can fail or never send onDone; the timeout still closes the call.
            handler.postDelayed(timeout, if (cycles == 2) 20_000 else 10_000)
            speech = TextToSpeech(context.applicationContext) { result ->
                handler.post {
                    if (stopped) return@post
                    if (result != TextToSpeech.SUCCESS) {
                        playFallback(cycles)
                        return@post
                    }
                    val engine = speech ?: return@post
                    val language = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                        playFallback(cycles)
                        return@post
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            handler.post { if (!stopped) onCycle(utteranceId.substringAfterLast('-').toIntOrNull() ?: 1) }
                        }

                        override fun onDone(utteranceId: String) {
                            if (utteranceId.endsWith("-$cycles")) handler.post { finish() }
                        }

                        override fun onError(utteranceId: String) {
                            handler.post { if (!stopped) playFallback(cycles) }
                        }
                    })
                    repeat(cycles) { index ->
                        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                        val resultCode = engine.speak(outcome.spokenPrompt, queueMode, Bundle(), "call-outcome-${index + 1}")
                        if (resultCode == TextToSpeech.ERROR) {
                            playFallback(cycles)
                            return@post
                        }
                    }
                }
            }
        }
    }

    private fun playFallback(cycles: Int) {
        if (stopped || fallbackStarted) return
        fallbackStarted = true
        speech?.stop()
        // The local ring/busy tone keeps this path audible when Chinese TTS is unavailable.
        playTone(ToneGenerator.TONE_SUP_RINGTONE, 2_000 * cycles, cycles)
    }

    private fun playTone(toneId: Int, durationMs: Int, cycles: Int) {
        handler.removeCallbacks(timeout)
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
        tone = generator
        if (generator == null) {
            handler.postDelayed(timeout, durationMs.toLong())
            return
        }
        val cycleLength = durationMs / cycles
        repeat(cycles) { index ->
            handler.postDelayed({
                if (!stopped) {
                    onCycle(index + 1)
                    generator.startTone(toneId, cycleLength)
                }
            }, (index * cycleLength).toLong())
        }
        handler.postDelayed(timeout, durationMs.toLong())
    }

    private fun finish() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        tone?.stopTone()
        tone?.release()
        speech?.stop()
        speech?.shutdown()
        onFinished()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        tone?.stopTone()
        tone?.release()
        speech?.stop()
        speech?.shutdown()
    }
}
