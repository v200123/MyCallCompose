package com.lastcoffee.top.oppo_call_compose.data

import android.content.Context
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomPhoneLocation(
    val number: String,
    val province: String,
    val city: String,
    val carrier: String,
) {
    val displayText: String
        get() = formatPhoneLocation(province, city, carrier)
}

internal fun formatPhoneLocation(province: String, city: String, carrier: String): String {
    val region = province.trim() + city.trim().takeUnless { it == province.trim() }.orEmpty()
    return listOf(region, carrier.trim()).filter(String::isNotBlank).joinToString(" ")
}

internal enum class CustomLocationSource { EXACT_NUMBER, SEVEN_DIGIT_PREFIX }

internal data class CustomLocationMatch(val location: CustomPhoneLocation, val source: CustomLocationSource)

private val mainlandMobilePattern = Regex("^1[3-9]\\d{9}$")
private val mainlandMobilePrefixPattern = Regex("^1[3-9]\\d{5}$")

fun isMainlandMobilePrefix(number: String): Boolean = mainlandMobilePrefixPattern.matches(normalizePhoneNumber(number))

private fun mainlandMobileDigits(number: String): String? {
    val digits = normalizePhoneNumber(number)
    val mobile = when {
        digits.length == 11 -> digits
        digits.length == 13 && digits.startsWith("86") -> digits.drop(2)
        digits.length == 15 && digits.startsWith("0086") -> digits.drop(4)
        else -> return null
    }
    return mobile.takeIf(mainlandMobilePattern::matches)
}

enum class CallPromptType(val title: String, val legacyStatusId: String) {
    POWER_OFF("关机提示音号码", "powered_off"),
    EMPTY_NUMBER("空号提示音号码", "empty_number"),
    BUSY("用户正忙提示音号码", "busy"),
    UNREACHABLE("无法接通提示音号码", "unreachable"),
}

data class PhoneExtras(
    val locations: Map<String, CustomPhoneLocation> = emptyMap(),
    val defaultRingbackId: String? = null,
    val ringbackBindings: Map<String, String> = emptyMap(),
    val noRingbackNumbers: Set<String> = emptySet(),
    val autoHangUpRules: Map<String, Int> = emptyMap(),
    val promptNumbers: Map<String, CallPromptType> = emptyMap(),
    val statusBindings: Map<String, String> = emptyMap(),
    val promptCategoryRules: Map<CallPromptType, String> = emptyMap(),
    val dialpadToneEnabled: Boolean = false,
    val dualSimDisplayEnabled: Boolean = false,
    val screenVariants: Map<String, CallScreenVariant> = emptyMap(),
    val avatarPromptCategoryRules: Map<CallPromptType, String> = emptyMap(),
    val avatarPhotos: Map<String, String> = emptyMap(),
) {
    fun locationFor(number: String): String? = customLocationMatch(number)?.location?.displayText

    internal fun customLocationMatch(number: String): CustomLocationMatch? {
        val digits = normalizePhoneNumber(number)
        locations[digits]?.let { return CustomLocationMatch(it, CustomLocationSource.EXACT_NUMBER) }
        val mobile = mainlandMobileDigits(digits) ?: return null
        return (locations[mobile] ?: locations["86$mobile"] ?: locations["0086$mobile"])
            ?.let { CustomLocationMatch(it, CustomLocationSource.EXACT_NUMBER) }
            ?: locations[mobile.take(7)]?.let { CustomLocationMatch(it, CustomLocationSource.SEVEN_DIGIT_PREFIX) }
    }

    fun blocksRingback(number: String): Boolean = normalizePhoneNumber(number) in noRingbackNumbers

    fun autoHangUpSecondsFor(number: String): Int? = autoHangUpRules[normalizePhoneNumber(number)]

    fun promptFor(number: String): CallPromptType? = promptNumbers[normalizePhoneNumber(number)]

    fun promptCategoryRulesFor(variant: CallScreenVariant): Map<CallPromptType, String> =
        if (variant == CallScreenVariant.WITH_AVATAR) avatarPromptCategoryRules else promptCategoryRules

    fun promptRuleFor(type: CallPromptType, variant: CallScreenVariant = CallScreenVariant.WITHOUT_AVATAR): String =
        promptCategoryRulesFor(variant)[type] ?: type.legacyStatusId

    fun promptRuleFor(number: String): String? = promptFor(number)?.let { promptRuleFor(it, screenVariantFor(number)) }

    fun statusFor(number: String): String? = statusBindings[normalizePhoneNumber(number)]

    fun screenVariantFor(number: String): CallScreenVariant =
        screenVariants[normalizePhoneNumber(number)] ?: CallScreenVariant.forNumber(number)

    fun avatarPhotoFor(number: String): String? = avatarPhotos[normalizePhoneNumber(number)]

    fun configuredNumbers(): Set<String> = locations.keys + ringbackBindings.keys + noRingbackNumbers +
        autoHangUpRules.keys + promptNumbers.keys + statusBindings.keys + screenVariants.keys + avatarPhotos.keys
}

fun normalizePhoneNumber(number: String): String = number.filter(Char::isDigit)

/** Small, local settings shared by the settings screens and the simulated call screen. */
class PhoneExtrasStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("phone_extras", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(decode(preferences.getString("settings", null)))
    val settings: StateFlow<PhoneExtras> = mutable

    fun exportSettings(): String = encode(mutable.value).toString()

    fun replaceSettings(raw: String) {
        val root = JSONObject(raw)
        require(root.has("locations") && root.has("ringbackBindings") && root.has("promptNumbers")) { "号码配置不完整" }
        val value = decode(raw)
        check(preferences.edit().putString("settings", encode(value).toString()).commit()) { "号码配置保存失败" }
        mutable.value = value
    }

    fun saveLocation(location: CustomPhoneLocation) = update {
        val enteredNumber = normalizePhoneNumber(location.number)
        val number = mainlandMobileDigits(enteredNumber) ?: enteredNumber
        it.copy(locations = (it.locations - enteredNumber) + (number to location.copy(number = number)))
    }

    fun deleteLocation(number: String) = update {
        it.copy(locations = it.locations - normalizePhoneNumber(number))
    }

    fun clearLocations() = update { it.copy(locations = emptyMap()) }

    fun setDefaultRingback(id: String?) = update { it.copy(defaultRingbackId = id) }

    fun setDialpadToneEnabled(enabled: Boolean) = update { it.copy(dialpadToneEnabled = enabled) }

    fun setDualSimDisplayEnabled(enabled: Boolean) = update { it.copy(dualSimDisplayEnabled = enabled) }

    fun bindRingback(number: String, id: String?) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(ringbackBindings = if (id == null) it.ringbackBindings - normalized else it.ringbackBindings + (normalized to id))
    }

    fun setRingbackBlocked(number: String, blocked: Boolean) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(noRingbackNumbers = if (blocked) it.noRingbackNumbers + normalized else it.noRingbackNumbers - normalized)
    }

    fun saveAutoHangUpRule(number: String, delaySeconds: Int, oldNumber: String? = null) = update { state ->
        val normalized = normalizePhoneNumber(number)
        val previous = oldNumber?.let(::normalizePhoneNumber)
        state.copy(autoHangUpRules = state.autoHangUpRules
            .let { if (previous != null) it - previous else it }
            .plus(normalized to delaySeconds.coerceIn(1, 3600)))
    }

    fun deleteAutoHangUpRule(number: String) = update {
        it.copy(autoHangUpRules = it.autoHangUpRules - normalizePhoneNumber(number))
    }

    fun clearAutoHangUpRules() = update { it.copy(autoHangUpRules = emptyMap()) }

    fun bindPromptCategoryRule(type: CallPromptType, ruleId: String, variant: CallScreenVariant = CallScreenVariant.WITHOUT_AVATAR) = update {
        if (variant == CallScreenVariant.WITH_AVATAR)
            it.copy(avatarPromptCategoryRules = it.avatarPromptCategoryRules + (type to ruleId))
        else it.copy(promptCategoryRules = it.promptCategoryRules + (type to ruleId))
    }

    fun savePromptNumber(number: String, type: CallPromptType) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(
            promptNumbers = it.promptNumbers + (normalized to type),
            statusBindings = it.statusBindings - normalized,
        )
    }

    fun deletePromptNumber(number: String) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(promptNumbers = it.promptNumbers - normalized)
    }

    fun clearPromptNumbers(type: CallPromptType) = update {
        val removed = it.promptNumbers.filterValues { selected -> selected == type }.keys
        it.copy(promptNumbers = it.promptNumbers - removed)
    }

    fun bindStatus(number: String, statusId: String?) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(
            statusBindings = if (statusId == null) it.statusBindings - normalized else it.statusBindings + (normalized to statusId),
            promptNumbers = if (statusId == null) it.promptNumbers else it.promptNumbers - normalized,
        )
    }

    fun setScreenVariant(number: String, variant: CallScreenVariant) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(screenVariants = it.screenVariants + (normalized to variant), statusBindings = it.statusBindings - normalized)
    }

    fun setCallRecordAvatar(number: String, hasAvatar: Boolean, photoPath: String?) = update {
        val normalized = normalizePhoneNumber(number)
        it.copy(
            screenVariants = it.screenVariants + (normalized to if (hasAvatar) CallScreenVariant.WITH_AVATAR else CallScreenVariant.WITHOUT_AVATAR),
            avatarPhotos = if (hasAvatar && photoPath != null) it.avatarPhotos + (normalized to photoPath)
                else it.avatarPhotos - normalized,
            statusBindings = it.statusBindings - normalized,
        )
    }

    fun removeMedia(id: String) = update {
        it.copy(
            defaultRingbackId = it.defaultRingbackId?.takeUnless { selected -> selected == id },
            ringbackBindings = it.ringbackBindings.filterValues { selected -> selected != id },
        )
    }

    private fun update(transform: (PhoneExtras) -> PhoneExtras) {
        val next = transform(mutable.value)
        preferences.edit().putString("settings", encode(next).toString()).apply()
        mutable.value = next
    }

    private fun encode(value: PhoneExtras) = JSONObject().apply {
        put("locations", JSONArray().apply {
            value.locations.values.forEach { location ->
                put(JSONObject().apply {
                    put("number", location.number)
                    put("province", location.province)
                    put("city", location.city)
                    put("carrier", location.carrier)
                })
            }
        })
        put("defaultRingbackId", value.defaultRingbackId)
        put("ringbackBindings", JSONObject(value.ringbackBindings))
        put("noRingbackNumbers", JSONArray(value.noRingbackNumbers.toList()))
        put("autoHangUpRules", JSONObject(value.autoHangUpRules))
        put("promptNumbers", JSONObject(value.promptNumbers.mapValues { it.value.name }))
        put("statusBindings", JSONObject(value.statusBindings))
        put("promptCategoryRules", JSONObject(value.promptCategoryRules.mapKeys { it.key.name }))
        put("avatarPromptCategoryRules", JSONObject(value.avatarPromptCategoryRules.mapKeys { it.key.name }))
        put("dialpadToneEnabled", value.dialpadToneEnabled)
        put("dualSimDisplayEnabled", value.dualSimDisplayEnabled)
        put("screenVariants", JSONObject(value.screenVariants.mapValues { it.value.name }))
        put("avatarPhotos", JSONObject(value.avatarPhotos))
    }

    private fun decode(raw: String?): PhoneExtras = runCatching {
        val root = JSONObject(raw ?: "{}")
        val locations = mutableMapOf<String, CustomPhoneLocation>()
        root.optJSONArray("locations")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val number = normalizePhoneNumber(item.optString("number"))
                if (number.isNotEmpty()) locations[number] = CustomPhoneLocation(
                    number, item.optString("province"), item.optString("city"), item.optString("carrier"),
                )
            }
        }
        val bindings = mutableMapOf<String, String>()
        root.optJSONObject("ringbackBindings")?.let { objectValue ->
            objectValue.keys().forEach { number ->
                val id = objectValue.optString(number)
                if (id.isNotBlank()) bindings[normalizePhoneNumber(number)] = id
            }
        }
        val blocked = mutableSetOf<String>()
        root.optJSONArray("noRingbackNumbers")?.let { array ->
            for (index in 0 until array.length()) blocked += normalizePhoneNumber(array.optString(index))
        }
        val autoHangUpRules = mutableMapOf<String, Int>()
        root.optJSONObject("autoHangUpRules")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val seconds = objectValue.optInt(rawNumber)
                if (number.matches(Regex("^1[3-9]\\d{9}$")) && seconds in 1..3600) autoHangUpRules[number] = seconds
            }
        }
        val promptNumbers = mutableMapOf<String, CallPromptType>()
        root.optJSONObject("promptNumbers")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val type = CallPromptType.entries.firstOrNull { it.name == objectValue.optString(rawNumber) }
                if (number.length in 3..20 && type != null) promptNumbers[number] = type
            }
        }
        val statusBindings = mutableMapOf<String, String>()
        root.optJSONObject("statusBindings")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val id = objectValue.optString(rawNumber)
                if (number.length in 3..20 && id.isNotBlank() && number !in promptNumbers) statusBindings[number] = id
            }
        }
        val screenVariants = mutableMapOf<String, CallScreenVariant>()
        root.optJSONObject("screenVariants")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val variant = CallScreenVariant.entries.firstOrNull { it.name == objectValue.optString(rawNumber) }
                if (number.length in 3..20 && variant != null) screenVariants[number] = variant
            }
        }
        val avatarPhotos = mutableMapOf<String, String>()
        root.optJSONObject("avatarPhotos")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val path = objectValue.optString(rawNumber)
                if (number.length in 3..20 && path.isNotBlank()) avatarPhotos[number] = path
            }
        }
        val promptCategoryRules = mutableMapOf<CallPromptType, String>()
        root.optJSONObject("promptCategoryRules")?.let { objectValue ->
            CallPromptType.entries.forEach { type ->
                objectValue.optString(type.name).takeIf { it.isNotBlank() }?.let { promptCategoryRules[type] = it }
            }
        }
        val avatarPromptCategoryRules = mutableMapOf<CallPromptType, String>()
        root.optJSONObject("avatarPromptCategoryRules")?.let { objectValue ->
            CallPromptType.entries.forEach { type ->
                objectValue.optString(type.name).takeIf { it.isNotBlank() }?.let { avatarPromptCategoryRules[type] = it }
            }
        }
        val oldPromptRules = mutableMapOf<String, String>()
        root.optJSONObject("promptRuleBindings")?.let { objectValue ->
            objectValue.keys().forEach { rawNumber ->
                val number = normalizePhoneNumber(rawNumber)
                val id = objectValue.optString(rawNumber)
                if (number in promptNumbers && id.isNotBlank()) oldPromptRules[number] = id
            }
        }
        CallPromptType.entries.forEach { type ->
            if (type !in promptCategoryRules) {
                oldPromptRules.entries.filter { promptNumbers[it.key] == type }
                    .groupingBy { it.value }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .firstOrNull()?.key?.let { promptCategoryRules[type] = it }
            }
        }
        if (!root.has("avatarPromptCategoryRules")) avatarPromptCategoryRules.putAll(promptCategoryRules)
        PhoneExtras(
            locations,
            root.optString("defaultRingbackId").takeIf { it.isNotBlank() && it != "null" },
            bindings,
            blocked,
            autoHangUpRules,
            promptNumbers,
            statusBindings,
            promptCategoryRules,
            dialpadToneEnabled = root.optBoolean("dialpadToneEnabled", false),
            dualSimDisplayEnabled = root.optBoolean("dualSimDisplayEnabled", false),
            screenVariants = screenVariants,
            avatarPromptCategoryRules = avatarPromptCategoryRules,
            avatarPhotos = avatarPhotos,
        )
    }.getOrDefault(PhoneExtras())
}
