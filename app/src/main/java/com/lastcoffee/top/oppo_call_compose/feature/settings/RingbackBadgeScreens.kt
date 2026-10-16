package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastcoffee.top.oppo_call_compose.data.BadgeAsset
import com.lastcoffee.top.oppo_call_compose.data.BadgePlacement
import com.lastcoffee.top.oppo_call_compose.data.BadgeRule
import com.lastcoffee.top.oppo_call_compose.data.BadgeSettings
import com.lastcoffee.top.oppo_call_compose.data.RingbackBadgeStore
import com.lastcoffee.top.oppo_call_compose.feature.call.RingbackBadgeCanvas

@Composable
fun RingbackBadgeRulesScreen(
    settings: BadgeSettings,
    store: RingbackBadgeStore,
    onOpenEditor: (String) -> Unit,
    onOpenBindings: () -> Unit,
    onBack: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BadgeRule?>(null) }
    var deleting by remember { mutableStateOf<BadgeRule?>(null) }
    ExtrasFrame("彩铃图标规则", onBack) {
        Text("图标叠加在彩铃视频上；每个号码固定使用一条规则。最多 20 条规则。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }, enabled = settings.rules.size < 20, modifier = Modifier.weight(1f)) { Text("新建规则") }
            Button(onClick = onOpenBindings, modifier = Modifier.weight(1f)) { Text("号码规则绑定") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(settings.rules, key = { it.id }) { rule ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RingbackBadgeCanvas(rule, modifier = Modifier.size(112.dp, 88.dp).background(Color.Black))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(rule.name, fontWeight = FontWeight.SemiBold)
                            Text("${rule.placements.size} 个图标${if (rule.isPreset) " · 预设" else ""}", color = Color.Gray)
                            Row {
                                TextButton(onClick = { onOpenEditor(rule.id) }) { Text("编辑") }
                                TextButton(onClick = { renaming = rule }) { Text("重命名") }
                                TextButton(onClick = { deleting = rule }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) RuleNameDialog("新建规则", "新规则", { creating = false }) { name ->
        store.createRule(name)?.let { onOpenEditor(it.id) }
        creating = false
    }
    renaming?.let { rule -> RuleNameDialog("重命名规则", rule.name, { renaming = null }) { name ->
        store.saveRule(rule.copy(name = name)); renaming = null
    } }
    deleting?.let { rule -> ConfirmDelete("删除规则「${rule.name}」及其号码绑定？", { deleting = null }) {
        store.deleteRule(rule.id); deleting = null
    } }
}

@Composable
private fun RuleNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("规则名称") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun RingbackBadgeEditorScreen(rule: BadgeRule, store: RingbackBadgeStore, onBack: () -> Unit) {
    var working by remember(rule.id) { mutableStateOf(rule) }
    var selected by remember(rule.id) { mutableStateOf<BadgeAsset?>(null) }
    fun saveAndBack() { store.saveRule(working); onBack() }
    BackHandler { saveAndBack() }
    ExtrasFrame(rule.name, ::saveAndBack) {
        Text("点击底部图标添加；拖动调整位置，双指缩放。返回时保存。", modifier = Modifier.padding(12.dp), color = Color.Gray)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("当前选中：${selected?.title ?: "未选中"}", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                working = working.copy(placements = working.placements.filterNot { it.asset == selected })
                selected = null
            }, enabled = selected != null) { Text("删除选中") }
            TextButton(onClick = { working = working.copy(placements = emptyList()); selected = null }, enabled = working.placements.isNotEmpty()) { Text("清空") }
        }
        RingbackBadgeCanvas(
            rule = working,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp).background(Color.Black),
            selected = selected,
            onSelect = { asset ->
                if (selected != asset) {
                    selected = asset
                    val top = (working.placements.maxOfOrNull { it.zIndex } ?: 0) + 1
                    working = working.copy(placements = working.placements.map { if (it.asset == asset) it.copy(zIndex = top) else it })
                }
            },
            onChange = { updated ->
                val top = (working.placements.maxOfOrNull { it.zIndex } ?: 0) + 1
                val moved = if (selected == updated.asset) updated else updated.copy(zIndex = top)
                working = working.copy(placements = working.placements.map { if (it.asset == moved.asset) moved else it })
                selected = moved.asset
            },
        )
        Text("资源栏：同一图标只能添加一次", color = Color.Gray, modifier = Modifier.padding(start = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeAsset.entries.forEach { asset ->
                Column(Modifier.background(Color.Black).clickable {
                    val existing = working.placements.firstOrNull { it.asset == asset }
                    val top = (working.placements.maxOfOrNull { it.zIndex } ?: 0) + 1
                    if (existing == null) {
                        working = working.copy(placements = working.placements + BadgePlacement(asset, zIndex = top))
                    } else {
                        working = working.copy(placements = working.placements.map { if (it.asset == asset) it.copy(zIndex = top) else it })
                    }
                    selected = asset
                }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(asset.drawable), contentDescription = asset.title, modifier = Modifier.size(56.dp))
                    Text(asset.title, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun RingbackBadgeBindingsScreen(settings: BadgeSettings, store: RingbackBadgeStore, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    val digits = query.filter(Char::isDigit)
    val filtered = settings.phoneBindings.toList().filter { (number, id) ->
        query.isBlank() || (digits.isNotEmpty() && number.contains(digits)) ||
            settings.rules.firstOrNull { it.id == id }?.name?.contains(query, ignoreCase = true) == true
    }.sortedBy { it.first }
    ExtrasFrame("号码图标规则", onBack) {
        Text("未手动绑定的号码会在首次播放彩铃时随机固定一条规则。", color = Color.Gray, modifier = Modifier.padding(16.dp))
        Button(onClick = { adding = true }, enabled = settings.rules.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("绑定号码") }
        OutlinedTextField(query, { query = it }, label = { Text("搜索号码或规则") }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.first }) { (number, id) ->
                ExtrasRow(number, settings.rules.firstOrNull { it.id == id }?.name ?: "规则已删除", onClick = { deleting = number }, onDelete = { deleting = number })
            }
        }
        if (settings.phoneBindings.isNotEmpty()) TextButton(onClick = { clearAll = true }, modifier = Modifier.fillMaxWidth()) { Text("清空号码绑定") }
    }
    if (adding) BadgeBindingDialog(settings.rules, onDismiss = { adding = false }) { numbers, id ->
        numbers.forEach { store.setPhoneRule(it, id) }; adding = false
    }
    deleting?.let { number -> ConfirmDelete("清除 $number 的图标规则绑定？", { deleting = null }) {
        store.setPhoneRule(number, null); deleting = null
    } }
    if (clearAll) ConfirmDelete("清空所有号码图标规则绑定？", { clearAll = false }) { store.clearBindings(); clearAll = false }
}

@Composable
private fun BadgeBindingDialog(rules: List<BadgeRule>, onDismiss: () -> Unit, onSave: (List<String>, String) -> Unit) {
    var raw by remember { mutableStateOf("") }
    var ruleId by remember { mutableStateOf(rules.firstOrNull()?.id) }
    var menu by remember { mutableStateOf(false) }
    val numbers = parseNumbers(raw)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("绑定图标规则") },
        text = { Column {
            OutlinedTextField(raw, { raw = it }, label = { Text("号码，支持换行或逗号分隔") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextButton(onClick = { menu = true }) { Text(rules.firstOrNull { it.id == ruleId }?.name ?: "选择规则") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                rules.forEach { rule -> DropdownMenuItem(text = { Text(rule.name) }, onClick = { ruleId = rule.id; menu = false }) }
            }
            Text("识别到 ${numbers.size} 个号码", color = Color.Gray)
        } },
        confirmButton = { TextButton(onClick = { ruleId?.let { onSave(numbers, it) } }, enabled = numbers.isNotEmpty() && ruleId != null) { Text("绑定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
