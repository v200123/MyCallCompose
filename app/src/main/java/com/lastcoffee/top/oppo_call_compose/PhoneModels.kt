package com.lastcoffee.top.oppo_call_compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.graphics.vector.ImageVector

enum class CallKind(val icon: ImageVector) {
    INCOMING(Icons.AutoMirrored.Filled.CallReceived),
    OUTGOING(Icons.AutoMirrored.Filled.CallMade),
    MISSED(Icons.AutoMirrored.Filled.CallReceived),
    BLOCKED(Icons.Filled.Block),
}

data class CallRecord(
    val number: String,
    val description: String,
    val date: String,
    val kind: CallKind,
    val tag: String? = null,
    val detailTime: String? = null,
    val durationDetail: String? = null,
    val storedId: String? = null,
    val demoIndex: Int? = null,
)

data class ContactMatch(
    val name: String,
    val number: String,
    val location: String,
)

val demoCalls = listOf(
    CallRecord("紧急呼叫中心", "112", "2026/9/17", CallKind.INCOMING),
    CallRecord("400 169 5559", "未知归属地", "2026/8/10", CallKind.BLOCKED, "广告推销"),
    CallRecord("192 3810 6207", "小布通话  四川成都  广电", "2026/8/9", CallKind.MISSED, "骚扰电话"),
    CallRecord("028 6237 0241", "小布通话\n四川成都/眉山/资阳", "2026/8/9", CallKind.MISSED, "广告推销"),
    CallRecord("158 8449 1032", "四川成都  移动", "2026/8/9", CallKind.BLOCKED),
    CallRecord("地坪漆", "177 0900 0007", "2026/5/28", CallKind.OUTGOING, detailTime = "2026/5/28 19:47", durationDetail = "呼出  35 秒"),
    CallRecord("地坪漆", "177 0900 0007", "2026/5/28", CallKind.OUTGOING, detailTime = "2026/5/28 10:43", durationDetail = "呼出  1分41秒"),
    CallRecord("地坪漆", "177 0900 0007", "2026/4/15", CallKind.OUTGOING, detailTime = "2026/4/15 10:16", durationDetail = "呼出  1分21秒"),
    CallRecord("157 5655 6141", "四川成都  移动", "2026/8/9", CallKind.OUTGOING),
    CallRecord("182 8449 1775", "小布通话  四川成都  移动", "2026/8/9", CallKind.OUTGOING),
    CallRecord("020 8759 4667", "广东广州", "2026/8/9", CallKind.BLOCKED),
    CallRecord("136 8908 0587", "小布通话  四川成都  移动", "2026/8/8", CallKind.OUTGOING),
)

val demoContacts = listOf(
    ContactMatch("川哥", "19136066659", "四川成都  电信"),
    ContactMatch("高春，华", "13658172895", "四川德阳  移动"),
    ContactMatch("黄立静", "13668392281", "四川达州  移动"),
    ContactMatch("LHuiMin", "18613602148", "四川成都  联通"),
    ContactMatch("张女士", "13690253318", "四川成都  移动"),
    ContactMatch("地坪漆", "177 0900 0007", "四川成都  移动"),
)
