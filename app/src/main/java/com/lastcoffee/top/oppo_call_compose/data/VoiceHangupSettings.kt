package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import org.json.JSONArray

data class VoiceHangupPhrase(val phrase: String, val enabled: Boolean)

class VoiceHangupSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("voice_hangup_settings", Context.MODE_PRIVATE)

    @Synchronized
    fun phrases(): List<VoiceHangupPhrase> {
        val values = try {
            val json = JSONArray(preferences.getString(KEY_PHRASES, null) ?: return listOf(VoiceHangupPhrase(DEFAULT_PHRASE, true)))
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val phrase = item.optString("phrase").trim()
                    if (phrase.isNotEmpty()) add(VoiceHangupPhrase(phrase, item.optBoolean("enabled")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return values.ifEmpty { listOf(VoiceHangupPhrase(DEFAULT_PHRASE, true)) }
    }

    fun selected(): List<String> = phrases().filter { it.enabled }.map { it.phrase }

    @Synchronized
    fun add(phrase: String) {
        val normalized = phrase.trim()
        if (normalized.isEmpty() || phrases().any { it.phrase == normalized }) return
        save(phrases() + VoiceHangupPhrase(normalized, false))
    }

    @Synchronized
    fun setEnabled(phrase: String, enabled: Boolean) {
        val current = phrases()
        if (!enabled && current.count { it.enabled } <= 1 && current.any { it.phrase == phrase && it.enabled }) return
        save(current.map { if (it.phrase == phrase) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun remove(phrase: String) {
        val current = phrases()
        val remaining = current.filterNot { it.phrase == phrase }
        if (remaining.isEmpty()) return
        val hadEnabled = current.any { it.phrase == phrase && it.enabled }
        save(if (hadEnabled && remaining.none { it.enabled }) remaining.mapIndexed { index, item -> item.copy(enabled = index == 0) } else remaining)
    }

    @Synchronized
    private fun save(values: List<VoiceHangupPhrase>) {
        val json = JSONArray()
        values.forEach { value ->
            json.put(org.json.JSONObject().put("phrase", value.phrase).put("enabled", value.enabled))
        }
        preferences.edit().putString(KEY_PHRASES, json.toString()).apply()
    }

    private companion object {
        const val KEY_PHRASES = "phrases"
        const val DEFAULT_PHRASE = "挂断电话"
    }
}
