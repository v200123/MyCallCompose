package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallStage

@Composable
fun CallPromptNumbersScreen(
    type: CallPromptType,
    settings: PhoneExtras,
    store: PhoneExtrasStore,
    configuration: CallFlowConfiguration,
    onBack: () -> Unit,
) {
    var adding by remember(type) { mutableStateOf(false) }
    var deleting by remember(type) { mutableStateOf<String?>(null) }
    var clearing by remember(type) { mutableStateOf(false) }
    val numbers = settings.promptNumbers.filterValues { it == type }.keys.sorted()
    val outcomeRules = configuration.statuses.filter { it.stage == CallStage.OUTCOME }
    val selectedRuleId = settings.promptRuleFor(type)
    val selectedRule = outcomeRules.firstOrNull { it.id == selectedRuleId }

    ExtrasFrame(type.title, onBack) {
        Text("此类全部号码统一执行所选规则；更换规则后立即应用到已有号码。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        BindingDropdown("提示规则", selectedRule?.name.orEmpty(), outcomeRules.map { it.id to it.name },
            onSelect = { store.bindPromptCategoryRule(type, it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (selectedRule == null) Text("请先选择有效的提示规则", color = Color.Red, modifier = Modifier.padding(horizontal = 16.dp))
        Button(onClick = { adding = true }, enabled = selectedRule != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("添加号码") }
        Text("已添加 ${numbers.size} 个号码", color = Color.Gray, modifier = Modifier.padding(16.dp))
        if (numbers.isEmpty()) Text("暂无记录", color = Color.Gray, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(numbers, key = { it }) { number ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(number, modifier = Modifier.weight(1f))
                        IconButton(onClick = { deleting = number }) { Icon(Icons.Default.Delete, contentDescription = "删除号码") }
                    }
                }
            }
        }
        if (numbers.isNotEmpty()) TextButton(onClick = { clearing = true }, modifier = Modifier.fillMaxWidth()) { Text("清空所有号码") }
    }
    if (adding) PromptNumberDialog(type, settings, onDismiss = { adding = false }) { number ->
        store.savePromptNumber(number, type); adding = false
    }
    deleting?.let { number -> ConfirmDelete("从${type.title}中删除 $number？", { deleting = null }) {
        store.deletePromptNumber(number); deleting = null
    } }
    if (clearing) ConfirmDelete("清空所有${type.title}？", { clearing = false }) {
        store.clearPromptNumbers(type); clearing = false
    }
}

@Composable
private fun PromptNumberDialog(
    type: CallPromptType,
    settings: PhoneExtras,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    val normalized = normalizePhoneNumber(number)
    val previousType = settings.promptFor(normalized)
    val valid = normalized.length in 3..20 && previousType != type
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("添加${type.title}") },
        text = { androidx.compose.foundation.layout.Column {
            OutlinedTextField(number, { number = it.filter(Char::isDigit).take(20) },
                label = { Text("电话号码（3–20 位）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                modifier = Modifier.fillMaxWidth())
            if (previousType == type) Text("该号码已在当前列表中", color = Color.Red)
            if (previousType != null && previousType != type) Text("该号码已有${previousType.title}，保存后会替换。", color = Color.Gray)
            if (number.isNotBlank() && normalized.length !in 3..20) Text("请输入 3–20 位电话号码", color = Color.Red)
        } },
        confirmButton = { TextButton(onClick = { onSave(normalized) }, enabled = valid) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BindingDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("请选择$label") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
