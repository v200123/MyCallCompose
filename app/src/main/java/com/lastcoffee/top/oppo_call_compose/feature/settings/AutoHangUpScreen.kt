package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.CallNumberPoolEntity
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber

@Composable
fun AutoHangUpScreen(settings: PhoneExtras, store: PhoneExtrasStore, entries: List<CallNumberPoolEntity>, onOpenPools: () -> Unit, onBack: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    val searchDigits = normalizePhoneNumber(search)
    val rules = settings.autoHangUpRules.toList().filter { searchDigits.isEmpty() || it.first.contains(searchDigits) }.sortedBy { it.first }

    ExtrasFrame("自动挂断号码", onBack) {
        Text("从自动操作号码池选择手机号；接通后达到设定秒数即挂断。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        Button(onClick = { adding = true }, enabled = entries.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("绑定自动挂断号码") }
        TextButton(onClick = onOpenPools, modifier = Modifier.padding(start = 12.dp)) { Text("自动操作规则绑定与号码管理") }
        OutlinedTextField(search, { search = it }, label = { Text("搜索号码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp))
        Text("已配置 ${settings.autoHangUpRules.size} 个号码", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
        if (rules.isEmpty()) Text(if (search.isBlank()) "暂无自动挂断号码" else "没有匹配的号码", color = Color.Gray, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(rules, key = { it.first }) { (number, seconds) ->
                ExtrasRow(number, "接通后 $seconds 秒自动挂断", onClick = { editing = number to seconds }, onDelete = { deleting = number to seconds })
            }
        }
        if (settings.autoHangUpRules.isNotEmpty()) TextButton(onClick = { clearAll = true }, modifier = Modifier.fillMaxWidth()) { Text("清空所有规则") }
    }
    if (adding) AutoHangUpEditor(null, entries.map { normalizePhoneNumber(it.number) }.distinct(), onDismiss = { adding = false }) { number, seconds ->
        store.saveAutoHangUpRule(number, seconds); adding = false
    }
    editing?.let { original -> AutoHangUpEditor(original, entries.map { normalizePhoneNumber(it.number) }.distinct(), onDismiss = { editing = null }) { number, seconds ->
        store.saveAutoHangUpRule(number, seconds, oldNumber = original.first); editing = null
    } }
    deleting?.let { (number, seconds) -> ConfirmDelete("删除 $number 的规则？接通后 $seconds 秒将不再自动挂断。", { deleting = null }) {
        store.deleteAutoHangUpRule(number); deleting = null
    } }
    if (clearAll) ConfirmDelete("清空所有自动挂断号码？", { clearAll = false }) { store.clearAutoHangUpRules(); clearAll = false }
}

@Composable
private fun AutoHangUpEditor(existing: Pair<String, Int>?, numbers: List<String>, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var number by remember(existing) { mutableStateOf(existing?.first ?: numbers.firstOrNull().orEmpty()) }
    var numberMenu by remember { mutableStateOf(false) }
    var secondsText by remember(existing) { mutableStateOf(existing?.second?.toString().orEmpty()) }
    val normalized = normalizePhoneNumber(number)
    val seconds = secondsText.toIntOrNull()
    val validNumber = normalized.matches(Regex("^1[3-9]\\d{9}$")) && normalized in numbers
    val validSeconds = seconds != null && seconds in 1..3600
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加自动挂断号码" else "编辑自动挂断号码") },
        text = { Column {
            Text("该号码在通话接通后达到设定秒数会自动挂断。", color = Color.Gray)
            Box(Modifier.padding(top = 12.dp)) {
                TextButton(onClick = { numberMenu = true }, enabled = existing == null) { Text(if (number.isBlank()) "选择号码池中的手机号" else "号码：$number") }
                DropdownMenu(numberMenu, { numberMenu = false }) {
                    numbers.forEach { candidate -> DropdownMenuItem(text = { Text(candidate) }, onClick = { number = candidate; numberMenu = false }) }
                }
            }
            OutlinedTextField(secondsText, { secondsText = it.filter(Char::isDigit).take(4) },
                label = { Text("接通后自动挂断秒数（1–3600）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            if (number.isNotBlank() && !validNumber) Text("请从号码池选择有效的 11 位手机号", color = Color.Red)
            if (secondsText.isNotBlank() && !validSeconds) Text("请输入 1–3600 秒", color = Color.Red)
        } },
        confirmButton = { TextButton(onClick = { onSave(normalized, seconds!!) }, enabled = validNumber && validSeconds) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
