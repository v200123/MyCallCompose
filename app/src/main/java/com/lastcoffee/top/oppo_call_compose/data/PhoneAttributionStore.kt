package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.ihxq.projects.pna.PhoneNumberLookup
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Resolves ordinary mobile numbers after the user's custom location rules. */
class PhoneAttributionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("phone_attributions", Context.MODE_PRIVATE)
    private val cached = runCatching {
        val json = JSONObject(preferences.getString("by_prefix", "{}") ?: "{}")
        buildMap {
            json.keys().forEach { key ->
                json.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())
    private val mutable = MutableStateFlow(cached)
    val locations: StateFlow<Map<String, String>> = mutable
    private val inFlight = mutableSetOf<String>()
    private val attempted = mutableSetOf<String>()
    private val lookup by lazy { PhoneNumberLookup() }

    fun locationFor(number: String): String? = mobileForLookup(number)?.take(7)?.let(mutable.value::get)

    suspend fun request(number: String) {
        val mobile = mobileForLookup(number) ?: return
        val prefix = mobile.take(7)
        synchronized(inFlight) {
            if (prefix in mutable.value || prefix in inFlight || prefix in attempted) return
            inFlight += prefix
        }
        try {
            val result = withContext(Dispatchers.IO) { localLocation(mobile) ?: onlineLocation(mobile) }
            if (!result.isNullOrBlank()) {
                mutable.value = mutable.value + (prefix to result)
                preferences.edit().putString("by_prefix", JSONObject(mutable.value).toString()).apply()
            }
        } finally {
            synchronized(inFlight) {
                inFlight -= prefix
                attempted += prefix
            }
        }
    }

    private fun localLocation(mobile: String): String? = runCatching {
        val info = lookup.lookup(mobile).orElse(null) ?: return@runCatching null
        val region = info.attribution
        val province = region?.province.orEmpty()
        val city = region?.city.orEmpty().takeUnless { it == province }.orEmpty()
        val carrier = info.isp?.cnName.orEmpty().removePrefix("中国")
        listOf(province, city, carrier).filter(String::isNotBlank).joinToString(" ").takeIf {
            province.isNotBlank() && province != "未知" && carrier.isNotBlank() && carrier != "未知"
        }
    }.getOrNull()

    private fun onlineLocation(mobile: String): String? = runCatching {
        val connection = URL("https://uapis.cn/api/v1/misc/phoneinfo?phone=$mobile")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) return@runCatching null
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val province = json.optString("province")
            val city = json.optString("city").takeUnless { it == province }.orEmpty()
            val carrier = sequenceOf("sp", "carrier", "isp")
                .map(json::optString).firstOrNull(String::isNotBlank).orEmpty().removePrefix("中国")
            listOf(province, city, carrier).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

internal fun mobileForLookup(number: String): String? {
    val digits = normalizePhoneNumber(number)
    val mobile = when {
        digits.length == 11 -> digits
        digits.length == 13 && digits.startsWith("86") -> digits.drop(2)
        digits.length == 15 && digits.startsWith("0086") -> digits.drop(4)
        else -> return null
    }
    return mobile.takeIf { it.matches(Regex("^1[3-9]\\d{9}$")) }
}
