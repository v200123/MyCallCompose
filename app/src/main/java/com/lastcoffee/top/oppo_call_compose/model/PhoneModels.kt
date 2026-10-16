package com.lastcoffee.top.oppo_call_compose.model

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
    val isUnanswered: Boolean = kind == CallKind.MISSED || kind == CallKind.BLOCKED,
)

data class ContactMatch(
    val name: String,
    val number: String,
    val location: String,
)
