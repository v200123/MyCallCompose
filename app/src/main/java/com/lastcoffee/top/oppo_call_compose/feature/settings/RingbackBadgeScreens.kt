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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.lastcoffee.top.oppo_call_compose.data.BadgeAsset
import com.lastcoffee.top.oppo_call_compose.data.BadgeAnimationStep
import com.lastcoffee.top.oppo_call_compose.data.BadgeAnimationType
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
    var animationQueueOpen by remember(rule.id) { mutableStateOf(false) }
    var previewAnimations by remember(rule.id) { mutableStateOf(false) }
    fun saveAndBack() { store.saveRule(working); onBack() }
    BackHandler { saveAndBack() }
    ExtrasFrame(rule.name, ::saveAndBack) {
        Text("点击底部图标添加；拖动调整位置，双指缩放。动画按队列顺序播放。返回时保存。", modifier = Modifier.padding(12.dp), color = Color.Gray)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("当前选中：${selected?.title ?: "未选中"}", modifier = Modifier.weight(1f))
            TextButton(onClick = { animationQueueOpen = true }, enabled = selected != null) { Text("动画队列") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                working = working.copy(placements = working.placements.filterNot { it.asset == selected })
                selected = null
            }, enabled = selected != null) { Text("删除选中") }
            TextButton(onClick = { working = working.copy(placements = emptyList()); selected = null }, enabled = working.placements.isNotEmpty()) { Text("清空") }
            TextButton(onClick = { previewAnimations = !previewAnimations }) {
                Text(if (previewAnimations) "停止预览" else "预览动画")
            }
        }
        RingbackBadgeCanvas(
            rule = working,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp).background(Color.Black),
            selected = selected,
            animated = previewAnimations,
            onSelect = { asset ->
                if (previewAnimations) return@RingbackBadgeCanvas
                if (selected != asset) {
                    selected = asset
                    val top = (working.placements.maxOfOrNull { it.zIndex } ?: 0) + 1
                    working = working.copy(placements = working.placements.map { if (it.asset == asset) it.copy(zIndex = top) else it })
                }
            },
            onChange = { updated ->
                if (previewAnimations) return@RingbackBadgeCanvas
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
    if (animationQueueOpen) {
        val placement = working.placements.firstOrNull { it.asset == selected }
        if (placement == null) animationQueueOpen = false else AnimationQueueDialog(
            placement = placement,
            onDismiss = { animationQueueOpen = false },
            onChange = { updated ->
                working = working.copy(placements = working.placements.map {
                    if (it.asset == updated.asset) updated else it
                })
            },
        )
    }
}

@Composable
private fun AnimationQueueDialog(placement: BadgePlacement, onDismiss: () -> Unit, onChange: (BadgePlacement) -> Unit) {
    var editingIndex by remember(placement.asset) { mutableStateOf<Int?>(null) }
    val steps = placement.animations
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${placement.asset.title} · 动画队列") },
        text = {
            Column {
                Text("每步先等待，再执行动画。位移相对图标原位。", color = Color.Gray)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("结束后恢复原位", modifier = Modifier.weight(1f))
                    Switch(checked = placement.restoreAfterAnimation,
                        onCheckedChange = { onChange(placement.copy(restoreAfterAnimation = it)) })
                }
                Text(if (placement.restoreAfterAnimation) "恢复后循环播放" else "播放一次，停留在最后状态", color = Color.Gray)
                if (steps.isEmpty()) Text("还没有动画步骤", modifier = Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(steps.size) { index ->
                        val step = steps[index]
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val action = when (step.type) {
                                BadgeAnimationType.SCALE -> "缩放至 ${step.targetScale} 倍"
                                BadgeAnimationType.TRANSLATE -> "平移 X ${step.xPercent * 100}% / Y ${step.yPercent * 100}%"
                                BadgeAnimationType.ROTATE -> "旋转至 ${step.targetRotation}°"
                            }
                            Text("${index + 1}. 等待 ${step.delaySeconds} 秒 · $action · ${step.durationSeconds} 秒")
                            Row {
                                TextButton(onClick = { editingIndex = index }) { Text("编辑") }
                                TextButton(onClick = {
                                    onChange(placement.copy(animations = steps.toMutableList().apply { add(index - 1, removeAt(index)) }))
                                }, enabled = index > 0) { Text("上移") }
                                TextButton(onClick = {
                                    onChange(placement.copy(animations = steps.toMutableList().apply { add(index + 1, removeAt(index)) }))
                                }, enabled = index < steps.lastIndex) { Text("下移") }
                                TextButton(onClick = { onChange(placement.copy(animations = steps.filterIndexed { i, _ -> i != index })) }) { Text("删除") }
                            }
                        }
                    }
                }
                TextButton(onClick = { editingIndex = steps.size }, enabled = steps.size < 12) { Text("添加步骤（最多 12 步）") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
    editingIndex?.let { index ->
        AnimationStepDialog(
            initial = steps.getOrNull(index),
            onDismiss = { editingIndex = null },
            onSave = { step ->
                if (index < steps.size) onChange(placement.copy(animations = steps.toMutableList().apply { this[index] = step }))
                else onChange(placement.copy(animations = steps + step))
                editingIndex = null
            },
        )
    }
}

@Composable
private fun AnimationStepDialog(initial: BadgeAnimationStep?, onDismiss: () -> Unit, onSave: (BadgeAnimationStep) -> Unit) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: BadgeAnimationType.SCALE) }
    var delay by remember(initial) { mutableStateOf((initial?.delaySeconds ?: 1f).toString()) }
    var duration by remember(initial) { mutableStateOf((initial?.durationSeconds ?: 0.5f).toString()) }
    var scale by remember(initial) { mutableStateOf((initial?.targetScale ?: 1.2f).toString()) }
    var x by remember(initial) { mutableStateOf(((initial?.xPercent ?: 0f) * 100).toString()) }
    var y by remember(initial) { mutableStateOf(((initial?.yPercent ?: 0f) * 100).toString()) }
    var rotation by remember(initial) { mutableStateOf((initial?.targetRotation ?: 90f).toString()) }
    val delayValue = delay.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..60f }
    val durationValue = duration.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.1f..10f }
    val scaleValue = scale.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.2f..2f }
    val xValue = x.toFloatOrNull()?.takeIf { it.isFinite() && it in -50f..50f }
    val yValue = y.toFloatOrNull()?.takeIf { it.isFinite() && it in -50f..50f }
    val rotationValue = rotation.toFloatOrNull()?.takeIf { it.isFinite() && it in -360f..360f }
    val valid = delayValue != null && durationValue != null &&
        when (type) {
            BadgeAnimationType.SCALE -> scaleValue != null
            BadgeAnimationType.TRANSLATE -> xValue != null && yValue != null
            BadgeAnimationType.ROTATE -> rotationValue != null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加动画步骤" else "编辑动画步骤") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    TextButton(onClick = { type = BadgeAnimationType.SCALE }) { Text(if (type == BadgeAnimationType.SCALE) "✓ 缩放" else "缩放") }
                    TextButton(onClick = { type = BadgeAnimationType.TRANSLATE }) { Text(if (type == BadgeAnimationType.TRANSLATE) "✓ 平移" else "平移") }
                    TextButton(onClick = { type = BadgeAnimationType.ROTATE }) { Text(if (type == BadgeAnimationType.ROTATE) "✓ 旋转" else "旋转") }
                }
                OutlinedTextField(delay, { delay = it }, label = { Text("执行前等待（秒，0–60）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(duration, { duration = it }, label = { Text("动画时长（秒，0.1–10）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                when (type) {
                    BadgeAnimationType.SCALE -> OutlinedTextField(scale, { scale = it }, label = { Text("目标倍数（0.2–2）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    BadgeAnimationType.TRANSLATE -> {
                        Text("位移按画布宽高的百分比计算，可填负数", color = Color.Gray)
                        OutlinedTextField(x, { x = it }, label = { Text("水平位移（%，-50–50）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(y, { y = it }, label = { Text("垂直位移（%，-50–50）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    BadgeAnimationType.ROTATE -> {
                        Text("正数顺时针，负数逆时针", color = Color.Gray)
                        OutlinedTextField(rotation, { rotation = it }, label = { Text("目标角度（°，-360–360）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave(BadgeAnimationStep(type, delayValue!!, durationValue!!, scaleValue ?: 1.2f,
                (xValue ?: 0f) / 100f, (yValue ?: 0f) / 100f, rotationValue ?: 90f))
        }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
