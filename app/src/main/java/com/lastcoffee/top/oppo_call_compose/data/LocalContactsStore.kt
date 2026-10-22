package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Contacts owned by this app. This store never accesses the system contacts provider. */
class LocalContactsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("local_contacts", Context.MODE_PRIVATE)
    private val mutableContacts = MutableStateFlow(load())
    val contacts: StateFlow<List<ContactMatch>> = mutableContacts.asStateFlow()

    fun save(contact: ContactMatch) {
        val saved = contact.copy(name = contact.name.trim(), number = contact.number.trim())
        if (saved.name.isBlank() || saved.number.isBlank()) return
        val updated = mutableContacts.value.toMutableList()
        // Keeping a selected contact's name lets the "save to existing" flow update its number.
        val index = updated.indexOfFirst { it.name == saved.name }.takeIf { it >= 0 }
            ?: updated.indexOfFirst { normalizedNumber(it.number) == normalizedNumber(saved.number) }
        if (index >= 0) updated[index] = saved else updated.add(saved)
        val json = JSONArray()
        updated.forEach {
            json.put(JSONObject().put("name", it.name).put("number", it.number).put("location", it.location))
        }
        preferences.edit().putString("contacts", json.toString()).apply()
        mutableContacts.value = updated
    }

    private fun load(): List<ContactMatch> {
        val json = preferences.getString("contacts", null) ?: return demoContacts
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val number = item.optString("number").trim()
                    if (name.isNotBlank() && number.isNotBlank()) {
                        add(ContactMatch(name, number, item.optString("location")))
                    }
                }
            }
        }.getOrElse { demoContacts }
    }

    private fun normalizedNumber(number: String): String = number.filter { it.isDigit() || it == '+' }
}
