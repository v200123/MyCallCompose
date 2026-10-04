package com.lastcoffee.top.oppo_call_compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
private const val KEYWORDS_FILE = "$MODEL_DIR/keywords.txt"
private const val SAMPLE_RATE = 16_000

fun voiceHangupPhrase(context: Context): String =
    context.assets.open(KEYWORDS_FILE).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.lineSequence().first { it.contains('@') }.substringAfter('@').trim()
    }

/** Listens only while the local demo call is active. It never captures telephone call audio. */
class VoiceHangupDetector(
    private val context: Context,
    private val onListening: () -> Unit,
    private val onKeyword: () -> Unit,
    private val onFailure: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var recorder: AudioRecord? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (job != null) return
        job = scope.launch {
            var spotter: KeywordSpotter? = null
            var stream: OnlineStream? = null
            var microphone: AudioRecord? = null
            try {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                val phrase = voiceHangupPhrase(context)

                spotter = KeywordSpotter(
                    assetManager = context.assets,
                    config = KeywordSpotterConfig(
                        featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = "$MODEL_DIR/encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
                                decoder = "$MODEL_DIR/decoder-epoch-13-avg-2-chunk-16-left-64.onnx",
                                joiner = "$MODEL_DIR/joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
                            ),
                            tokens = "$MODEL_DIR/tokens.txt",
                            modelType = "zipformer2",
                        ),
                        keywordsFile = KEYWORDS_FILE,
                        keywordsThreshold = 0.35f,
                    ),
                )
                stream = spotter.createStream()
                check(stream.ptr != 0L) { "Cannot create keyword stream" }

                val minBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minBytes > 0) { "Microphone format unavailable" }
                microphone = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes, SAMPLE_RATE / 5 * 2),
                )
                check(microphone.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                recorder = microphone
                microphone.startRecording()
                withContext(Dispatchers.Main) { onListening() }

                val pcm = ShortArray(SAMPLE_RATE / 10)
                while (isActive) {
                    val count = microphone.read(pcm, 0, pcm.size)
                    if (count <= 0) {
                        if (isActive) error("Microphone read failed: $count")
                        break
                    }
                    stream.acceptWaveform(FloatArray(count) { pcm[it] / 32768.0f }, SAMPLE_RATE)
                    while (isActive && spotter.isReady(stream)) {
                        spotter.decode(stream)
                        val keyword = spotter.getResult(stream).keyword
                        if (keyword.isNotBlank()) {
                            spotter.reset(stream)
                            if (keyword == phrase) {
                                withContext(Dispatchers.Main) { onKeyword() }
                                return@launch
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("VoiceHangupDetector", "Keyword detection failed", error)
                withContext(Dispatchers.Main) { onFailure() }
            } finally {
                recorder = null
                try {
                    if (microphone?.recordingState == AudioRecord.RECORDSTATE_RECORDING) microphone.stop()
                } catch (_: IllegalStateException) {
                    // The microphone may already have been stopped during disposal.
                }
                microphone?.release()
                stream?.release()
                spotter?.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // A concurrent read may have already stopped recording.
        }
        scope.cancel()
    }
}
