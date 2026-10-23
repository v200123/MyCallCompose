package com.lastcoffee.top.oppo_call_compose.model

/** Audio collection assigned to a simulated phone number. */
enum class CallAudioMode(val title: String) {
    DEFAULT_TONE("普通铃声"), RINGBACK("彩铃"), MUSIC("音乐"), STATUS_SOUND("状态音效"), DIALPAD_TONE("拨号按键音");

    // DIALPAD_TONE is retained only to read media from older configuration packages.

    val isCallMode: Boolean get() = this == DEFAULT_TONE || this == RINGBACK || this == MUSIC

    fun acceptsMimeType(mimeType: String): Boolean = when (this) {
        DEFAULT_TONE, MUSIC, STATUS_SOUND, DIALPAD_TONE -> mimeType.startsWith("audio/")
        RINGBACK -> mimeType.startsWith("video/")
    }

    companion object {
        fun fromStored(value: String?): CallAudioMode = entries.firstOrNull { it.name == value } ?: DEFAULT_TONE
    }
}

enum class CallStage(val title: String) {
    DIALING("正在呼叫"), WAITING("等待接听"), OUTCOME("播放提示"), ACTIVE("通话中"), HOLD("保持"),
    DISCONNECTING("正在挂断"), DISCONNECTED("已挂断")
}

enum class CallButtonId(val title: String) {
    MUTE("静音"), SPEAKER("扬声器"), KEYPAD("拨号盘"), RECORD("录音"), HOLD("保持"),
    ADD_CALL("添加通话"), VIDEO_CALL("视频通话"), AI_CALL_ASSISTANT("AI 通话助手"), HANGUP("挂断")
}

/** Per-element layout and paint settings used by both the editor and call screen. */
data class CallVisualStyle(
    val offsetXdp: Int = 0,
    val offsetYdp: Int = 0,
    val fontSizeSp: Float? = null,
    val textColorArgb: Long? = null,
    val backgroundColorArgb: Long? = null,
    val visible: Boolean = true,
)

data class CallButtonRule(
    val id: CallButtonId,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    /** Empty means run the built-in action; otherwise transition to this status. */
    val targetStatusId: String? = null,
    /** Optional label shown on the live call screen. */
    val label: String? = null,
    val visualStyle: CallVisualStyle = CallVisualStyle(),
)

data class CallStatusRule(
    val id: String,
    val name: String,
    val stage: CallStage,
    val durationSeconds: Int = 0,
    val timeoutTargetId: String? = null,
    val buttons: List<CallButtonRule> = defaultButtons(stage),
    val outcome: String? = null,
    val mediaEntryId: String? = null,
    val soundEntryId: String? = null,
    val playSoundOnce: Boolean = false,
    val soundMuted: Boolean = false,
    val callerNameStyle: CallVisualStyle = CallVisualStyle(),
    val callerNumberStyle: CallVisualStyle = CallVisualStyle(),
    val locationTextStyle: CallVisualStyle = CallVisualStyle(),
    val statusTextStyle: CallVisualStyle = CallVisualStyle(),
)

data class CallFlowConfiguration(
    val initialStatusId: String = "dialing",
    val selectedAudioMode: CallAudioMode = CallAudioMode.DEFAULT_TONE,
    val statuses: List<CallStatusRule> = defaultCallStatuses(),
)

data class CallMediaEntry(
    val id: String,
    val mode: CallAudioMode,
    val title: String,
    val filePath: String,
    val mimeType: String,
    val iconPath: String? = null,
)

data class CallSessionSetup(
    val mode: CallAudioMode,
    val initialStatusId: String,
    val configuration: CallFlowConfiguration,
)

fun defaultButtons(stage: CallStage): List<CallButtonRule> {
    val outgoing = stage == CallStage.DIALING || stage == CallStage.WAITING
    val active = stage == CallStage.ACTIVE || stage == CallStage.HOLD
    return CallButtonId.entries.map { id ->
        CallButtonRule(id = id, visible = true, enabled = when (id) {
            CallButtonId.MUTE, CallButtonId.KEYPAD -> active
            CallButtonId.SPEAKER -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
            CallButtonId.RECORD -> stage == CallStage.ACTIVE
            CallButtonId.ADD_CALL, CallButtonId.AI_CALL_ASSISTANT -> active
            CallButtonId.HOLD -> active || outgoing
            CallButtonId.VIDEO_CALL -> active || outgoing
            CallButtonId.HANGUP -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
        })
    }
}

fun defaultCallStatuses(): List<CallStatusRule> = listOf(
    CallStatusRule("dialing", "正在呼叫", CallStage.DIALING, 1, "waiting"),
    CallStatusRule("waiting", "等待接听", CallStage.WAITING, 30, "no_answer"),
    CallStatusRule("no_answer", "无人接听", CallStage.OUTCOME, 20, "disconnecting", outcome = "NO_ANSWER"),
    CallStatusRule("busy", "对方忙线", CallStage.OUTCOME, 5, "disconnecting", outcome = "BUSY"),
    CallStatusRule("empty_number", "空号", CallStage.OUTCOME, 10, "disconnecting", outcome = "EMPTY_NUMBER"),
    CallStatusRule("powered_off", "对方已关机", CallStage.OUTCOME, 10, "disconnecting", outcome = "POWERED_OFF"),
    CallStatusRule("unreachable", "无法接通", CallStage.OUTCOME, 10, "disconnecting", outcome = "UNREACHABLE"),
    CallStatusRule("active", "通话中", CallStage.ACTIVE),
    CallStatusRule("hold", "保持", CallStage.HOLD),
    CallStatusRule("disconnecting", "正在挂断", CallStage.DISCONNECTING, 1, "disconnected"),
    CallStatusRule("disconnected", "已挂断", CallStage.DISCONNECTED, 1),
)
