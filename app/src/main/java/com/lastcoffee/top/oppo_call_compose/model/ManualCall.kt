package com.lastcoffee.top.oppo_call_compose.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ManualCall(
    val id: String = UUID.randomUUID().toString(),
    val number: String,
    val kind: CallKind,
    val durationSeconds: Int,
    val timeMillis: Long,
    val wasConnected: Boolean = true,
) {
    fun toCallRecord(): CallRecord {
        val time = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault())
        val type = kind.displayLabel()
        val detail = if (kind == CallKind.MISSED || kind == CallKind.BLOCKED ||
            (kind == CallKind.OUTGOING && !wasConnected)
        ) "未接通" else formatCallDuration(durationSeconds)
        return CallRecord(
            number = number,
            description = "$type  $detail",
            date = time.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
            kind = kind,
            detailTime = time.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")),
            durationDetail = "$type  $detail",
            storedId = id,
        )
    }
}

fun CallKind.displayLabel(): String = when (this) {
    CallKind.INCOMING -> "呼入"
    CallKind.OUTGOING -> "呼出"
    CallKind.MISSED -> "未接来电"
    CallKind.BLOCKED -> "拒接"
}

fun formatCallDuration(seconds: Int): String = when {
    seconds >= 3_600 -> "${seconds / 3_600}小时${(seconds % 3_600) / 60}分${seconds % 60}秒"
    seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds}秒"
}
