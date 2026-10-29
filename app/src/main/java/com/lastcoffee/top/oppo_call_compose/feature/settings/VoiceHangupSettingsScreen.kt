package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.lastcoffee.top.oppo_call_compose.data.VoiceHangupPhrase
import com.lastcoffee.top.oppo_call_compose.data.VoiceHangupSettingsStore
import com.lastcoffee.top.oppo_call_compose.feature.call.phoneticKeywordLine
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

@Composable
fun VoiceHangupSettingsScreen(store: VoiceHangupSettingsStore, onBack: () -> Unit) {
    val phrases = remember(store) { mutableStateListOf<VoiceHangupPhrase>().apply { addAll(store.phrases()) } }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(color = Color(0xFFF1F2F5), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                Text("语音挂断口令", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("添加自己的口令并勾选启用。通话中说出任一已启用口令，都会执行挂断。", color = Color(0xFF707782), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("输入中文口令") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                IconButton(onClick = {
                    val phrase = input.trim()
                    try {
                        require(phrase.codePointCount(0, phrase.length) in 2..10) { "口令长度需为 2–10 个汉字" }
                        phoneticKeywordLine(phrase)
                        require(phrases.none { it.phrase == phrase }) { "这个口令已添加" }
                        require(phrases.size < 20) { "最多添加 20 个口令" }
                        store.add(phrase)
                        phrases.add(VoiceHangupPhrase(phrase, false))
                        input = ""
                    } catch (failure: Exception) {
                        error = failure.message ?: "口令无法识别，请检查后重试"
                    }
                }) { Icon(Icons.Filled.Add, contentDescription = "添加口令") }
            }
            error?.let { Text(it, color = Color(0xFFB3261E), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).background(Color.White, RoundedCornerShape(20.dp))) {
                phrases.forEach { phrase ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = phrase.enabled, onCheckedChange = { checked ->
                            if (!checked && phrases.count { it.enabled } <= 1) {
                                error = "至少保留一个启用口令"
                            } else {
                                store.setEnabled(phrase.phrase, checked)
                                phrases[phrases.indexOfFirst { it.phrase == phrase.phrase }] = phrase.copy(enabled = checked)
                            }
                        })
                        Text(phrase.phrase, modifier = Modifier.weight(1f).clickable {
                            if (phrase.enabled && phrases.count { it.enabled } <= 1) {
                                error = "至少保留一个启用口令"
                            } else {
                                store.setEnabled(phrase.phrase, !phrase.enabled)
                                phrases[phrases.indexOfFirst { it.phrase == phrase.phrase }] = phrase.copy(enabled = !phrase.enabled)
                            }
                        }, fontSize = 18.sp)
                        IconButton(onClick = {
                            store.remove(phrase.phrase)
                            phrases.remove(phrase)
                            if (phrases.none { it.enabled } && phrases.isNotEmpty()) {
                                phrases[0] = phrases[0].copy(enabled = true)
                            }
                        }, enabled = phrases.size > 1) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除${phrase.phrase}", tint = Color(0xFF858585))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 540)
@Composable
private fun VoiceHangupSettingsScreenPreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
        VoiceHangupSettingsScreen(VoiceHangupSettingsStore(LocalContext.current), onBack = {})
    }
}
