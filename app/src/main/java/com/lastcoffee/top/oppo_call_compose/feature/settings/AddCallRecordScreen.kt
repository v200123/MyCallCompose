package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.model.displayLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val CallTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
private val PhoneNumberPattern = Regex("[+0-9*#()\\-\\s]{1,32}")

@Composable
fun AddCallRecordScreen(manualCount: Int, onBack: () -> Unit, onSave: suspend (ManualCall) -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CallKind.OUTGOING) }
    var minutes by remember { mutableStateOf("0") }
    var seconds by remember { mutableStateOf("0") }
    var callTime by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    var kindMenuOpen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val hasDuration = kind == CallKind.OUTGOING || kind == CallKind.INCOMING

    fun pickCallTime() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedDate = LocalDate.of(year, month + 1, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute -> callTime = selectedDate.atTime(hour, minute) },
                    callTime.hour,
                    callTime.minute,
                    true,
                ).show()
            },
            callTime.year,
            callTime.monthValue - 1,
            callTime.dayOfMonth,
        ).show()
    }

    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("添加通话记录", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("手动添加通话记录", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("新增记录仅保存在本机，当前已添加 $manualCount 条", color = Color(0xFF757575), fontSize = 13.sp)

                    OutlinedTextField(
                        value = number,
                        onValueChange = {
                            if (it.length <= 32) number = it
                            errorMessage = null
                        },
                        label = { Text("拨打号码") },
                        placeholder = { Text("例如 13800138000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("拨打类型", fontSize = 14.sp, color = Color(0xFF666666))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { kindMenuOpen = true }) {
                                Text(kind.displayLabel())
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = kindMenuOpen, onDismissRequest = { kindMenuOpen = false }) {
                                CallKind.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayLabel()) },
                                        onClick = {
                                            kind = option
                                            kindMenuOpen = false
                                            errorMessage = null
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("通话时长", fontSize = 14.sp, color = Color(0xFF666666))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = if (hasDuration) minutes else "0",
                                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) minutes = it },
                                label = { Text("分钟") },
                                singleLine = true,
                                enabled = hasDuration,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = if (hasDuration) seconds else "0",
                                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) seconds = it },
                                label = { Text("秒钟") },
                                singleLine = true,
                                enabled = hasDuration,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!hasDuration) Text("未接来电和拒接的时长为 0 秒", fontSize = 12.sp, color = Color(0xFF858585))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("通话时间", fontSize = 14.sp, color = Color(0xFF666666))
                        OutlinedButton(onClick = ::pickCallTime, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(callTime.format(CallTimeFormat))
                        }
                    }

                    errorMessage?.let { Text(it, color = Color(0xFFBD3A32), fontSize = 13.sp) }
                    Button(
                        onClick = {
                            val cleanNumber = number.trim()
                            val minuteValue = minutes.toIntOrNull()
                            val secondValue = seconds.toIntOrNull()
                            errorMessage = when {
                                !PhoneNumberPattern.matches(cleanNumber) || cleanNumber.none(Char::isDigit) -> "请输入有效号码"
                                hasDuration && (minuteValue == null || secondValue == null || secondValue !in 0..59) -> "请输入有效时长，秒数为 0–59"
                                else -> null
                            }
                            if (errorMessage == null && !isSaving) {
                                val duration = if (hasDuration) minuteValue!! * 60 + secondValue!! else 0
                                val call = ManualCall(
                                    number = cleanNumber,
                                    kind = kind,
                                    durationSeconds = duration,
                                    timeMillis = callTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                )
                                isSaving = true
                                scope.launch {
                                    if (!onSave(call)) errorMessage = "保存失败，请重试"
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20AD45)),
                    ) {
                        Text("保存通话记录")
                    }
                }
            }
        }
    }
}
