package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.launch

private val callTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val callZone = ZoneId.of("Asia/Shanghai")

@Composable
fun AddCallRecordScreen(
    onBack: () -> Unit,
    onSave: suspend (ManualCall) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var outgoing by rememberSaveable { mutableStateOf(true) }
    var durationText by rememberSaveable { mutableStateOf("") }
    var callTimeMillis by rememberSaveable {
        mutableStateOf(LocalDateTime.now(callZone).withSecond(0).withNano(0).atZone(callZone).toInstant().toEpochMilli())
    }
    val callTime = Instant.ofEpochMilli(callTimeMillis).atZone(callZone).toLocalDateTime()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun pickCallTime() {
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDate = LocalDate.of(year, month + 1, day)
            TimePickerDialog(context, { _, hour, minute ->
                callTimeMillis = selectedDate.atTime(hour, minute).atZone(callZone).toInstant().toEpochMilli()
            }, callTime.hour, callTime.minute, true).show()
        }, callTime.year, callTime.monthValue - 1, callTime.dayOfMonth).show()
    }

    Surface(color = Color(0xFFF7F7F7), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("添加通话记录", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldTitle("对方号码")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { if (it.length <= 32) number = it; errorMessage = null },
                        placeholder = { Text("对方手机号") },
                        singleLine = true,
                        textStyle = TextStyle(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val contents = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        val digits = contents?.filter(Char::isDigit).orEmpty()
                        if (digits.isNotEmpty()) number = digits
                        else Toast.makeText(context, if (contents.isNullOrEmpty()) "剪贴板为空" else "剪贴板中没有有效的手机号", Toast.LENGTH_SHORT).show()
                    }) { Text("粘贴") }
                }

                FieldTitle("通话类型")
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { outgoing = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = outgoing, onClick = { outgoing = true })
                        Text("呼出电话")
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable { outgoing = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !outgoing, onClick = { outgoing = false })
                        Text("呼入电话")
                    }
                }

                FieldTitle("通话日期")
                Button(onClick = ::pickCallTime, modifier = Modifier.fillMaxWidth()) { Text(callTime.format(callTimeFormat)) }

                FieldTitle("通话时长（单位秒）")
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) durationText = it; errorMessage = null },
                    singleLine = true,
                    textStyle = TextStyle(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let { Text(it, color = Color(0xFFBD3A32), fontSize = 13.sp) }
                Button(onClick = {
                    val digits = normalizePhoneNumber(number)
                    val duration = durationText.toIntOrNull()
                    errorMessage = when {
                        digits.isEmpty() -> "请输入有效号码"
                        duration == null -> "请输入通话时长（秒）"
                        duration > 86_400 -> "通话时长不能超过 86400 秒"
                        else -> null
                    }
                    if (errorMessage == null && !isSaving) {
                        val start = callTimeMillis
                        val connected = duration!! > 0
                        val call = ManualCall(
                            number = digits,
                            kind = if (outgoing) CallKind.OUTGOING else if (connected) CallKind.INCOMING else CallKind.MISSED,
                            durationSeconds = duration,
                            timeMillis = start,
                            wasConnected = connected,
                            connectedTimeMillis = if (connected) start else 0,
                            endTimeMillis = start + duration * 1000L,
                            callNumber = if (!outgoing && !connected) Random.nextInt(5, 13) else 0,
                        )
                        isSaving = true
                        scope.launch {
                            if (!onSave(call)) errorMessage = "保存失败，请重试"
                            isSaving = false
                        }
                    }
                }, enabled = !isSaving, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("生成通话记录") }
            }
        }
    }
}

@Composable
private fun FieldTitle(label: String) {
    Text(label, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center, fontSize = 15.sp)
}
