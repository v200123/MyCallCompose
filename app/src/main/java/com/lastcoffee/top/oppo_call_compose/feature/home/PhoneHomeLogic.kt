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

/** CallLogGroupHandler groups consecutive equal normalized numbers, not all history. */
internal fun groupHomeCalls(records: List<CallRecord>, missedOnly: Boolean = false): List<HomeCallGroup> {
    val groups = mutableListOf<MutableList<CallRecord>>()
    records.sortedByDescending { it.timeMillis }.forEach { record ->
        val previous = groups.lastOrNull()
        if (previous != null && record.homeNumber().isNotEmpty() && previous.first().homeNumber() == record.homeNumber()) {
            previous.add(record)
        } else groups.add(mutableListOf(record))
    }
    return groups.map { HomeCallGroup(it) }.filter { !missedOnly || it.latest.kind == CallKind.MISSED }
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
    query.isNotEmpty() && number.filter { it.isDigit() || it in "+*#" }.contains(query)

internal fun matchingDialContacts(contacts: List<ContactMatch>, query: String): List<ContactMatch> =
    contacts.filter { dialMatchesNumber(it.number, query) }

internal fun CallRecord.homeAttribution(resolved: String?): String =
    resolved?.takeIf(String::isNotBlank)
        ?: listOfNotNull(attribution?.takeIf(String::isNotBlank), operator?.takeIf(String::isNotBlank))
            .joinToString(" ").ifBlank { description.takeUnless { it.startsWith("呼") || it.startsWith("未接") || it == "拒接" }.orEmpty() }
