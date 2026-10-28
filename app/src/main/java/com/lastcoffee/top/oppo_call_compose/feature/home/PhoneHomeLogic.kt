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

internal fun matchingDialContacts(contacts: List<ContactMatch>, query: String): List<ContactMatch> =
    contacts.filter { dialMatchesNumber(it.number, query) }.distinctBy { dialSearchNumberKey(it.number) }

internal fun CallRecord.homeAttribution(resolved: String?): String =
    resolved?.takeIf(String::isNotBlank)
        ?: listOfNotNull(attribution?.filterNot(Char::isWhitespace)?.takeIf(String::isNotBlank), operator?.trim()?.takeIf(String::isNotBlank))
            .joinToString(" ").ifBlank {
                description.takeUnless { it.startsWith("呼") || it.startsWith("未接") || it == "拒接" || it == "陌生号码" }
                    .orEmpty().ifBlank { "未知归属地" }
            }
