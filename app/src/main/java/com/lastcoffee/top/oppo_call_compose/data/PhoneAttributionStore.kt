package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
    private val loggingEnabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val preferences = context.applicationContext.getSharedPreferences("phone_attributions", Context.MODE_PRIVATE)
    private val cached = runCatching {
        val json = JSONObject(preferences.getString("by_prefix", "{}") ?: "{}")
        buildMap {
            json.keys().forEach { key ->
                json.optString(key).takeIf(String::isNotBlank)?.let { put(key, normalizeCachedPhoneLocation(it)) }
            }
        }
    }.getOrDefault(emptyMap())
    private val mutable = MutableStateFlow(cached)
    val locations: StateFlow<Map<String, String>> = mutable
    private val inFlight = mutableSetOf<String>()
    private val attempted = mutableSetOf<String>()
    private val lookup by lazy { PhoneNumberLookup() }

    fun locationFor(number: String): String? = mobileForLookup(number)?.take(7)?.let(mutable.value::get)

    suspend fun request(number: String, custom: PhoneExtras) {
        custom.customLocationMatch(number)?.let { match ->
            log("custom_hit", number, "type=${match.source} location=${match.location.displayText}")
            return
        }
        val mobile = mobileForLookup(number) ?: run {
            if (normalizePhoneNumber(number).length >= 11) log("skip_invalid", number)
            return
        }
        log("custom_miss", mobile)
        val prefix = mobile.take(7)
        synchronized(inFlight) {
            if (prefix in mutable.value) {
                log("cache_hit", mobile, "location=${mutable.value[prefix]}")
                return
            }
            if (prefix in inFlight) {
                log("skip_in_flight", mobile)
                return
            }
            if (prefix in attempted) {
                log("skip_already_attempted", mobile)
                return
            }
            inFlight += prefix
        }
        try {
            val result = withContext(Dispatchers.IO) { localLocation(mobile) ?: onlineLocation(mobile) }
            if (!result.isNullOrBlank()) {
                mutable.value = mutable.value + (prefix to result)
                preferences.edit().putString("by_prefix", JSONObject(mutable.value).toString()).apply()
                log("cache_saved", mobile, "location=$result")
            } else {
                log("lookup_empty", mobile)
            }
        } finally {
            synchronized(inFlight) {
                inFlight -= prefix
                attempted += prefix
            }
        }
    }

    private fun localLocation(mobile: String): String? {
        log("local_start", mobile)
        return try {
            val info = lookup.lookup(mobile).orElse(null)
            val region = info?.attribution
            val province = region?.province.orEmpty()
            val city = region?.city.orEmpty().takeUnless { it == province }.orEmpty()
            val carrier = info?.isp?.cnName.orEmpty().removePrefix("中国")
            val result = formatPhoneLocation(province, city, carrier).takeIf {
                province.isNotBlank() && province != "未知" && carrier.isNotBlank() && carrier != "未知"
            }
            if (result == null) log("local_miss", mobile) else log("local_hit", mobile, "location=$result")
            result
        } catch (error: LinkageError) {
            // Missing classes or a failed static initializer must still allow the online fallback.
            log("local_unavailable", mobile, "cause=${error.javaClass.simpleName}", warning = true)
            null
        } catch (error: Exception) {
            log("local_error", mobile, "cause=${error.javaClass.simpleName}", warning = true)
            null
        }
    }

    private fun onlineLocation(mobile: String): String? {
        log("api_start", mobile, "provider=UAPI")
        return try {
            val connection = URL("https://uapis.cn/api/v1/misc/phoneinfo?phone=$mobile")
                .openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                val status = connection.responseCode
                if (status != HttpsURLConnection.HTTP_OK) {
                    log("api_http_error", mobile, "status=$status", warning = true)
                    return null
                }
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val province = json.optString("province")
                val city = json.optString("city").takeUnless { it == province }.orEmpty()
                val carrier = sequenceOf("sp", "carrier", "isp")
                    .map(json::optString).firstOrNull(String::isNotBlank).orEmpty().removePrefix("中国")
                val result = formatPhoneLocation(province, city, carrier).takeIf(String::isNotBlank)
                if (result == null) log("api_empty", mobile, warning = true)
                else log("api_hit", mobile, "location=$result")
                result
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            log("api_error", mobile, "cause=${error.javaClass.simpleName}", warning = true)
            null
        }
    }

    private fun log(event: String, number: String, detail: String = "", warning: Boolean = false) {
        if (!loggingEnabled) return
        val mobile = mobileForLookup(number)
        val digits = normalizePhoneNumber(number)
        val masked = when {
            mobile != null -> "${mobile.take(7)}****"
            digits.length >= 7 -> "${digits.take(3)}****${digits.takeLast(2)}"
            else -> "length=${digits.length}"
        }
        val message = "$event number=$masked${if (detail.isBlank()) "" else " $detail"}"
        if (warning) Log.w("PhoneLocation", message) else Log.d("PhoneLocation", message)
    }
}

internal fun normalizeCachedPhoneLocation(value: String): String {
    val parts = value.trim().split(Regex("\\s+"))
    return if (parts.size == 3) "${parts[0]}${parts[1]} ${parts[2]}" else value.trim()
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
