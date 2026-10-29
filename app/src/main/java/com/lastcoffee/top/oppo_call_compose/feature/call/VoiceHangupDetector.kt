package com.lastcoffee.top.oppo_call_compose.feature.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.lastcoffee.top.oppo_call_compose.data.VoiceHangupSettingsStore
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
private const val SAMPLE_RATE = 16_000
private val MODEL_FILES = listOf(
    "encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
    "decoder-epoch-13-avg-2-chunk-16-left-64.onnx",
    "joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx",
    "tokens.txt",
)

fun voiceHangupPhrase(context: Context): String =
    VoiceHangupSettingsStore(context).selected().joinToString("、").ifBlank { "语音口令" }

/** Creates a sherpa keyword line from a Chinese phrase using this model's pinyin token format. */
fun phoneticKeywordLine(phrase: String): String {
    require(phrase.isNotBlank() && phrase.all { it in '\u3400'..'\u9fff' }) { "请只输入中文汉字口令" }
    val format = HanyuPinyinOutputFormat().apply {
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }
    val syllables = phrase.map { character ->
        val pinyin = PinyinHelper.toHanyuPinyinStringArray(character, format)?.firstOrNull()
            ?: error("无法生成“$character”的拼音")
        val initial = listOf("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w")
            .firstOrNull { pinyin.startsWith(it) && pinyin.length > it.length }
        if (initial == null) pinyin else "$initial ${pinyin.substring(initial.length)}"
    }
    return "${syllables.joinToString(" ")} @$phrase"
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
                val phrases = VoiceHangupSettingsStore(context).selected()
                check(phrases.isNotEmpty()) { "No enabled hangup phrases" }
                val keywordsFile = java.io.File(context.filesDir, "voice-hangup-keywords.txt").apply {
                    writeText(phrases.joinToString("\n") { phoneticKeywordLine(it) }, Charsets.UTF_8)
                }
                val modelDirectory = java.io.File(context.filesDir, MODEL_DIR).apply { mkdirs() }
                MODEL_FILES.forEach { name ->
                    val target = java.io.File(modelDirectory, name)
                    val assetPath = "$MODEL_DIR/$name"
                    if (!target.exists() || target.length() == 0L) {
                        context.assets.open(assetPath).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    }
                }

                spotter = KeywordSpotter(
                    assetManager = null,
                    config = KeywordSpotterConfig(
                        featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = java.io.File(modelDirectory, MODEL_FILES[0]).absolutePath,
                                decoder = java.io.File(modelDirectory, MODEL_FILES[1]).absolutePath,
                                joiner = java.io.File(modelDirectory, MODEL_FILES[2]).absolutePath,
                            ),
                            tokens = java.io.File(modelDirectory, MODEL_FILES[3]).absolutePath,
                            modelType = "zipformer2",
                        ),
                        keywordsFile = keywordsFile.absolutePath,
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
                            if (keyword in phrases) {
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
