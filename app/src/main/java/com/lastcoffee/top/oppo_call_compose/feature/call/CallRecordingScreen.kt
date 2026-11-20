package com.lastcoffee.top.oppo_call_compose.feature.call

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoCardPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CallRecordingScreen(
    call: ManualCall,
    file: File?,
    onSave: suspend (Uri?, Long?) -> Unit,
    onBack: () -> Unit,
) {
    var selectedUri by rememberSaveable(call.id) { mutableStateOf<String?>(null) }
    var secondsText by rememberSaveable(call.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri.toString()
            message = null
        }
    }
    val seconds = secondsText.toLongOrNull()?.takeIf { it > 0 }
    val validDuration = secondsText.isEmpty() || seconds != null
    val draftAudio = remember(selectedUri) { selectedUri?.let(Uri::parse) }
    val playableAudio = draftAudio ?: file?.let(Uri::fromFile)
    val record = remember(call) { call.toCallRecord() }
    BackHandler(enabled = saving) {}
    Surface(color = OppoCardPage, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !saving, onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("通话录音", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(call.number, style = MaterialTheme.typography.headlineSmall)
                Text(listOfNotNull(record.detailTime, record.durationDetail).joinToString(" · "))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (draftAudio != null) {
                            Text("所选录音预览")
                        } else if (call.recordingDurationMillis > 0) {
                            Text("显示时长：${formatRecordingTime(call.recordingDurationMillis)}")
                        }
                        if (playableAudio != null) {
                            RecordingPlayer(playableAudio)
                        } else {
                            Text(if (call.recordingDurationMillis > 0) "此记录只有显示时长" else "暂无录音")
                        }
                    }
                }
                Row {
                    TextButton(enabled = !saving, onClick = { picker.launch(arrayOf("audio/*")) }) {
                        Text(when {
                            draftAudio != null -> "重新选择录音"
                            file != null -> "更换本地录音文件"
                            else -> "添加录音文件"
                        })
                    }
                    if (draftAudio != null) {
                        TextButton(enabled = !saving, onClick = { selectedUri = null; message = null }) { Text("取消选择") }
                    }
                }
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter(Char::isDigit).take(8); message = null },
                    label = { Text("显示时长（秒，可选）") },
                    supportingText = { Text("留空时使用所选录音的实际时长。也可只保存时长。") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !validDuration,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !saving && validDuration && (selectedUri != null || seconds != null),
                    onClick = {
                        saving = true
                        message = null
                        scope.launch {
                            try {
                                onSave(draftAudio, seconds?.times(1000))
                                selectedUri = null
                                secondsText = ""
                                saveFailed = false
                                message = "录音信息已保存"
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                saveFailed = true
                                message = error.message ?: "录音保存失败"
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text(if (saving) "正在保存…" else "保存") }
                message?.let { Text(it, color = if (saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun RecordingPlayer(audio: Uri) {
    val context = LocalContext.current
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    var player by remember(audio) { mutableStateOf<MediaPlayer?>(null) }
    var duration by remember(audio) { mutableIntStateOf(0) }
    var position by remember(audio) { mutableIntStateOf(0) }
    var seekingTo by remember(audio) { mutableStateOf<Int?>(null) }
    var playing by remember(audio) { mutableStateOf(false) }
    var failed by remember(audio) { mutableStateOf(false) }
    DisposableEffect(context, audio, lifecycle) {
        val media = MediaPlayer()
        fun fail() {
            player = null
            playing = false
            failed = true
        }
        try {
            media.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(context, audio)
            media.setOnPreparedListener {
                duration = it.duration.coerceAtLeast(0)
                player = it
            }
            media.setOnCompletionListener {
                playing = false
                position = 0
                runCatching { it.seekTo(0) }
            }
            media.setOnErrorListener { _, _, _ -> fail(); true }
            media.prepareAsync()
        } catch (_: Exception) {
            fail()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && playing) {
                runCatching { media.pause() }
                playing = false
            }
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            media.setOnPreparedListener(null)
            media.setOnCompletionListener(null)
            media.setOnErrorListener(null)
            media.release()
        }
    }
    LaunchedEffect(player, playing) {
        val media = player ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        while (true) {
            position = runCatching { media.currentPosition }.getOrElse { return@LaunchedEffect }
            delay(150)
        }
    }
    if (failed) {
        Text("录音文件无法播放", color = MaterialTheme.colorScheme.error)
    } else if (player == null) {
        Text("正在加载录音…")
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val media = player ?: return@TextButton
                runCatching { if (playing) media.pause() else media.start() }
                    .onSuccess { playing = !playing }
                    .onFailure { failed = true; playing = false }
            }) { Text(if (playing) "暂停录音" else "播放录音") }
            Text("${formatRecordingTime((seekingTo ?: position).toLong())} / ${formatRecordingTime(duration.toLong())}")
        }
        if (duration > 0) {
            Slider(
                value = (seekingTo ?: position).coerceIn(0, duration).toFloat(),
                onValueChange = { seekingTo = it.roundToInt() },
                onValueChangeFinished = {
                    seekingTo?.let { target ->
                        runCatching { player?.seekTo(target) }.onSuccess { position = target }
                    }
                    seekingTo = null
                },
                valueRange = 0f..duration.toFloat(),
            )
        }
    }
}

private fun formatRecordingTime(millis: Long): String {
    val seconds = millis / 1000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
