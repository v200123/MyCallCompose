package com.lastcoffee.top.oppo_call_compose.feature.home

import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HomeCallGroup(val calls: List<CallRecord>) {
    val latest: CallRecord get() = calls.first()
    val key: String get() = latest.storedId ?: "${latest.number}:${latest.detailTime}:${latest.date}"
}

internal fun CallRecord.homeNumber(): String =
    (if (number.any(Char::isLetter)) description else number).filter { it.isDigit() || it in "+*#" }

/** Show each dialable number once, with its calls ordered from newest to oldest. */
internal fun groupHomeCalls(records: List<CallRecord>, missedOnly: Boolean = false): List<HomeCallGroup> {
    val groups = linkedMapOf<String, MutableList<CallRecord>>()
    records.asSequence()
        .filter { !missedOnly || it.kind == CallKind.MISSED }
        .sortedByDescending { it.timeMillis }
        .forEachIndexed { index, record ->
            val key = record.homeNumber().ifEmpty { "unknown:$index" }
            groups.getOrPut(key) { mutableListOf() }.add(record)
        }
    return groups.values.map(::HomeCallGroup)
}

internal fun homeCallDate(record: CallRecord, today: LocalDate = LocalDate.now()): String {
    val time = if (record.timeMillis > 0) {
        Instant.ofEpochMilli(record.timeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } else runCatching { LocalDateTime.parse(record.detailTime, DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")) }.getOrNull()
    val date = time?.toLocalDate()
        ?: runCatching { LocalDate.parse(record.date, DateTimeFormatter.ofPattern("yyyy/M/d")) }.getOrNull()
        ?: return record.date
    return when {
        date == today && time != null -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("M月d日"))
        else -> date.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
    }
}

internal fun dialMatchesNumber(number: String, query: String): Boolean =
    query.isNotEmpty() && dialSearchNumberKey(number).contains(query.filter { it.isDigit() || it in "+*#" })

/** Normalize common China mobile formats so +86 and local-format numbers compare equally. */
internal fun dialSearchNumberKey(number: String): String {
    val dialable = number.filter { it.isDigit() || it in "+*#" }
    val digits = dialable.filter(Char::isDigit)
    return when {
        digits.length == 13 && digits.startsWith("86") -> digits.drop(2)
        '*' in dialable || '#' in dialable -> dialable
        else -> digits
    }
}

/** Keep phone formatting out of the raw dial string, including pasted tel: links. */
internal fun pastedDialNumber(text: String): String = buildString {
    text.trim().removePrefix("tel:").substringBefore('?').forEach { character ->
        when {
            character in '0'..'9' || character in "*#,;" -> append(character)
            character == '+' && isEmpty() -> append(character)
        }
    }
}.takeIf { value -> value.any { it in '0'..'9' || it in "*#" } }.orEmpty()

/** The paste shortcut accepts a standalone number or dial code. */
internal fun qualifiedClipboardDialNumber(text: String): String? {
    val trimmed = text.trim()
    val formatted = if (trimmed.startsWith("tel:", ignoreCase = true)) {
        trimmed.drop(4).substringBefore('?')
    } else trimmed
    val number = formatted.filterNot { it in " -()\u00A0\u202F" }
    if (number.length > 100 || !number.all(Char::isDialCharacter) || '+' in number.drop(1)) return null

    val network = number.takeWhile { it != ',' && it != ';' }
    val digits = network.count { it in '0'..'9' }
    val valid = if ('*' in network || '#' in network) digits > 0 else digits in 3..15
    return number.takeIf { valid }
}

/** Raw dial characters and their selection, independent of the formatted display. */
internal data class DialInput(
    val number: String = "",
    val selectionStart: Int = number.length,
    val selectionEnd: Int = selectionStart,
) {
    fun insert(value: String): DialInput {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, number.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, number.length)
        val inserted = value.take((100 - number.length + end - start).coerceAtLeast(0))
        return DialInput(number.replaceRange(start, end, inserted), start + inserted.length)
    }

    fun delete(): DialInput {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, number.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, number.length)
        return when {
            start != end -> DialInput(number.removeRange(start, end), start)
            start > 0 -> DialInput(number.removeRange(start - 1, start), start - 1)
            else -> this
        }
    }
}

internal fun Char.isDialCharacter(): Boolean = this in '0'..'9' || this in "+*#,;"

internal fun rawDialOffset(display: String, offset: Int): Int = display.take(offset.coerceIn(0, display.length)).count { it.isDialCharacter() }

internal fun formattedDialOffset(display: String, rawOffset: Int): Int {
    if (rawOffset <= 0) return 0
    var seen = 0
    display.forEachIndexed { index, character ->
        if (character.isDialCharacter() && ++seen == rawOffset) return index + 1
    }
    return display.length
}

internal fun matchingDialContacts(contacts: List<ContactMatch>, query: String): List<ContactMatch> =
    contacts.filter { dialMatchesNumber(it.number, query) }.distinctBy { dialSearchNumberKey(it.number) }

internal fun CallRecord.homeAttribution(resolved: String?): String =
    resolved?.takeIf(String::isNotBlank)
        ?: listOfNotNull(attribution?.filterNot(Char::isWhitespace)?.takeIf(String::isNotBlank), operator?.trim()?.takeIf(String::isNotBlank))
            .joinToString(" ").ifBlank {
                description.takeUnless { it.startsWith("呼") || it.startsWith("未接") || it == "拒接" || it == "陌生号码" }
                    .orEmpty().ifBlank { "未知归属地" }
            }
