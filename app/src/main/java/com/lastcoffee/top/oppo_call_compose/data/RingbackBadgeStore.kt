package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import androidx.annotation.DrawableRes
import com.lastcoffee.top.oppo_call_compose.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BadgeAsset(val title: String, @param:DrawableRes val drawable: Int, val widthDp: Float, val heightDp: Float) {
    ONE("图标1", R.drawable.ic_call_1_badge, 132f, 35.33f),
    TWO("图标2", R.drawable.ic_call_2_badge, 20.67f, 280f),
    THREE("图标3", R.drawable.ic_call_3_badge, 88f, 28.67f),
    FOUR("图标4", R.drawable.ic_call_4_badge, 120f, 16f),
    FIVE("图标5", R.drawable.ic_call_5, 570.33f, 284.67f),
}

data class BadgePlacement(
    val asset: BadgeAsset,
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val scale: Float = 1f,
    val zIndex: Int = 0,
    val animations: List<BadgeAnimationStep> = emptyList(),
    val restoreAfterAnimation: Boolean = true,
)

enum class BadgeAnimationType { SCALE, TRANSLATE, ROTATE }

/** Each step waits, animates to its target, then hands control to the next step. */
data class BadgeAnimationStep(
    val type: BadgeAnimationType,
    val delaySeconds: Float = 1f,
    val durationSeconds: Float = 0.5f,
    val targetScale: Float = 1.2f,
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val targetRotation: Float = 90f,
)

private fun BadgeAnimationStep.sanitized() = copy(
    delaySeconds = delaySeconds.takeIf(Float::isFinite)?.coerceIn(0f, 60f) ?: 1f,
    durationSeconds = durationSeconds.takeIf(Float::isFinite)?.coerceIn(0.1f, 10f) ?: 0.5f,
    targetScale = targetScale.takeIf(Float::isFinite)?.coerceIn(0.2f, 2f) ?: 1.2f,
    xPercent = xPercent.takeIf(Float::isFinite)?.coerceIn(-0.5f, 0.5f) ?: 0f,
    yPercent = yPercent.takeIf(Float::isFinite)?.coerceIn(-0.5f, 0.5f) ?: 0f,
    targetRotation = targetRotation.takeIf(Float::isFinite)?.coerceIn(-360f, 360f) ?: 90f,
)

data class BadgeRule(
    val id: String,
    val name: String,
    val placements: List<BadgePlacement> = emptyList(),
    val isPreset: Boolean = false,
)

data class BadgeSettings(
    val rules: List<BadgeRule> = defaultBadgeRules(),
    val phoneBindings: Map<String, String> = emptyMap(),
) {
    fun ruleFor(number: String): BadgeRule? = rules.firstOrNull { it.id == phoneBindings[normalizePhoneNumber(number)] }
}

/** Rules for the artwork layered over a playing ringback video. */
class RingbackBadgeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ringback_badge_rules", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(decode(preferences.getString("settings", null)))
    val settings: StateFlow<BadgeSettings> = mutable

    fun exportSettings(): String = encode(mutable.value).toString()

    fun replaceSettings(raw: String) {
        val root = JSONObject(raw)
        require(root.has("rules") && root.has("phoneBindings")) { "图标规则不完整" }
        val value = decode(raw)
        check(preferences.edit().putString("settings", encode(value).toString()).commit()) { "图标规则保存失败" }
        mutable.value = value
    }

    fun createRule(name: String): BadgeRule? {
        if (mutable.value.rules.size >= 20) return null
        val rule = BadgeRule(UUID.randomUUID().toString(), name.trim().ifBlank { "新规则" })
        update { it.copy(rules = it.rules + rule) }
        return rule
    }

    fun saveRule(rule: BadgeRule) = update { state ->
        state.copy(rules = state.rules.map { if (it.id == rule.id) rule.copy(
            name = rule.name.trim().ifBlank { it.name },
            placements = rule.placements.distinctBy { placement -> placement.asset }.map { placement -> placement.copy(
                xPercent = placement.xPercent.coerceIn(0f, 1f),
                yPercent = placement.yPercent.coerceIn(0f, 1f),
                scale = placement.scale.coerceIn(0.2f, 2f),
                animations = placement.animations.take(12).map(BadgeAnimationStep::sanitized),
            ) },
        ) else it })
    }

    fun deleteRule(id: String) = update { state ->
        state.copy(rules = state.rules.filterNot { it.id == id }, phoneBindings = state.phoneBindings.filterValues { it != id })
    }

    fun setPhoneRule(number: String, ruleId: String?) = update { state ->
        val normalized = normalizePhoneNumber(number)
        state.copy(phoneBindings = if (ruleId == null) state.phoneBindings - normalized else state.phoneBindings + (normalized to ruleId))
    }

    fun clearBindings() = update { it.copy(phoneBindings = emptyMap()) }

    fun assignRuleForPhone(number: String): BadgeRule? {
        val normalized = normalizePhoneNumber(number)
        val state = mutable.value
        state.ruleFor(normalized)?.let { return it }
        val picked = state.rules.randomOrNull() ?: return null
        if (normalized.isNotBlank()) setPhoneRule(normalized, picked.id)
        return picked
    }

    private fun update(change: (BadgeSettings) -> BadgeSettings) {
        val next = change(mutable.value)
        preferences.edit().putString("settings", encode(next).toString()).apply()
        mutable.value = next
    }

    private fun encode(value: BadgeSettings) = JSONObject().apply {
        put("rules", JSONArray().apply { value.rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("isPreset", rule.isPreset)
                put("placements", JSONArray().apply { rule.placements.forEach { placement ->
                    put(JSONObject().apply {
                        put("asset", placement.asset.name)
                        put("x", placement.xPercent.toDouble())
                        put("y", placement.yPercent.toDouble())
                        put("scale", placement.scale.toDouble())
                        put("z", placement.zIndex)
                        put("restoreAfterAnimation", placement.restoreAfterAnimation)
                        put("animations", JSONArray().apply { placement.animations.forEach { step ->
                            put(JSONObject().apply {
                                put("type", step.type.name)
                                put("delay", step.delaySeconds.toDouble())
                                put("duration", step.durationSeconds.toDouble())
                                put("targetScale", step.targetScale.toDouble())
                                put("x", step.xPercent.toDouble())
                                put("y", step.yPercent.toDouble())
                                put("targetRotation", step.targetRotation.toDouble())
                            })
                        } })
                    })
                } })
            })
        } })
        put("phoneBindings", JSONObject(value.phoneBindings))
    }

    private fun decode(raw: String?): BadgeSettings = runCatching {
        val root = JSONObject(raw ?: "{}")
        val rules = root.optJSONArray("rules")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val placements = item.optJSONArray("placements")?.let { images ->
                    (0 until images.length()).mapNotNull { imageIndex ->
                        val image = images.optJSONObject(imageIndex) ?: return@mapNotNull null
                        val asset = BadgeAsset.entries.firstOrNull { it.name == image.optString("asset") } ?: return@mapNotNull null
                        val animations = image.optJSONArray("animations")?.let { steps ->
                            (0 until minOf(steps.length(), 12)).mapNotNull { stepIndex ->
                                val step = steps.optJSONObject(stepIndex) ?: return@mapNotNull null
                                val type = BadgeAnimationType.entries.firstOrNull { it.name == step.optString("type") }
                                    ?: return@mapNotNull null
                                BadgeAnimationStep(type, step.optDouble("delay", 1.0).toFloat(),
                                    step.optDouble("duration", 0.5).toFloat(),
                                    step.optDouble("targetScale", 1.2).toFloat(),
                                    step.optDouble("x", 0.0).toFloat(), step.optDouble("y", 0.0).toFloat(),
                                    step.optDouble("targetRotation", 90.0).toFloat()).sanitized()
                            }
                        }.orEmpty()
                        BadgePlacement(asset, image.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                            image.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                            image.optDouble("scale", 1.0).toFloat().coerceIn(0.2f, 2f), image.optInt("z"),
                            animations, image.optBoolean("restoreAfterAnimation", true))
                    }
                }.orEmpty()
                BadgeRule(id, item.optString("name", "规则"), placements.distinctBy { it.asset }, item.optBoolean("isPreset"))
            }
        } ?: defaultBadgeRules()
        val bindings = mutableMapOf<String, String>()
        root.optJSONObject("phoneBindings")?.let { objectValue ->
            objectValue.keys().forEach { number -> bindings[normalizePhoneNumber(number)] = objectValue.optString(number) }
        }
        BadgeSettings(rules.take(20), bindings)
    }.getOrDefault(BadgeSettings())
}

private fun defaultBadgeRules(): List<BadgeRule> {
    fun image(asset: BadgeAsset, x: Float, y: Float, z: Int) = BadgePlacement(asset, x, y, zIndex = z)
    return listOf(
        listOf(image(BadgeAsset.THREE, .87f, .88f, 0)),
        listOf(image(BadgeAsset.ONE, .82f, .12f, 0), image(BadgeAsset.THREE, .87f, .88f, 1)),
        listOf(image(BadgeAsset.FOUR, .18f, .12f, 0), image(BadgeAsset.TWO, .16f, .52f, 1), image(BadgeAsset.THREE, .87f, .88f, 2)),
        listOf(image(BadgeAsset.ONE, .50f, .12f, 0), image(BadgeAsset.THREE, .87f, .88f, 1)),
        listOf(image(BadgeAsset.FOUR, .18f, .12f, 0), image(BadgeAsset.TWO, .16f, .52f, 1)),
        listOf(image(BadgeAsset.FOUR, .18f, .12f, 0)),
        listOf(image(BadgeAsset.TWO, .16f, .52f, 0), image(BadgeAsset.THREE, .87f, .88f, 1)),
    ).mapIndexed { index, placements -> BadgeRule("preset_rule_${index + 1}", "预设规则${index + 1}", placements, isPreset = true) }
}
