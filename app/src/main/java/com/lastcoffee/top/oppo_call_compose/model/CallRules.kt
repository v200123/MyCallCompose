package com.lastcoffee.top.oppo_call_compose.model

internal const val BUILT_IN_WAITING_TONE_ID = "builtin_waiting"

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
    VOICEMAIL("语音留言"),
    DISCONNECTING("正在挂断"), DISCONNECTED("已挂断")
}

enum class CallStatusDisplayMode(val title: String) {
    DEFAULT("默认显示"), TEXT_ONLY("仅显示文字"), TIMER_ONLY("仅显示计时"), TEXT_AND_TIMER("文字和计时");

    companion object {
        fun fromStored(value: String?): CallStatusDisplayMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class CallScreenVariant(val title: String) {
    WITHOUT_AVATAR("无头像"), WITH_AVATAR("有头像");

    companion object {
        fun forNumber(number: String): CallScreenVariant =
            if (number.filter(Char::isDigit) == "10086") WITH_AVATAR else WITHOUT_AVATAR
    }
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
    /** Total audio plays. 0 keeps the previous unlimited-loop behavior. */
    val audioPlayCount: Int = 0,
    val callerNameStyle: CallVisualStyle = CallVisualStyle(),
    val callerNumberStyle: CallVisualStyle = CallVisualStyle(),
    val locationTextStyle: CallVisualStyle = CallVisualStyle(),
    val statusTextStyle: CallVisualStyle = CallVisualStyle(),
    val useDefaultPromptWait: Boolean = false,
    val displayText: String? = null,
    val displayMode: CallStatusDisplayMode = CallStatusDisplayMode.DEFAULT,
)

internal data class CallStatusDisplay(val text: String?, val showsTimer: Boolean)

internal fun CallStatusRule.defaultDisplayText(): String = when {
    id == "powered_off" && name == "对方已关机" -> "您拨打的号码已关机"
    id == "empty_number" && name == "空号" -> "您拨打的号码是空号"
    id == "in_call" && name == "正在通话中" -> "对方通话中，请稍后再拨"
    id == "no_answer" && name == "无人接听" -> "等待接听"
    id == "unreachable" && name == "无法接通" -> "您拨打的号码暂时无法接通"
    else -> name
}

internal fun CallStatusRule.statusDisplay(hasConnected: Boolean): CallStatusDisplay {
    val customText = displayText?.takeIf(String::isNotBlank)
    val text = customText ?: defaultDisplayText()
    return when (displayMode) {
        CallStatusDisplayMode.TEXT_ONLY -> CallStatusDisplay(text, false)
        CallStatusDisplayMode.TIMER_ONLY -> CallStatusDisplay(null, true)
        CallStatusDisplayMode.TEXT_AND_TIMER -> CallStatusDisplay(text, true)
        CallStatusDisplayMode.DEFAULT -> {
            val defaultText = when {
                customText != null -> customText
                hasConnected && (stage == CallStage.DISCONNECTING || stage == CallStage.DISCONNECTED) -> "通话结束"
                (stage == CallStage.ACTIVE || stage == CallStage.VOICEMAIL) && name == "通话中" -> null
                else -> text
            }
            CallStatusDisplay(defaultText, hasConnected)
        }
    }
}

data class CallFlowConfiguration(
    val initialStatusId: String = "dialing",
    val selectedAudioMode: CallAudioMode = CallAudioMode.DEFAULT_TONE,
    val statuses: List<CallStatusRule> = defaultCallStatuses(),
)

val coreCallStatusIds = setOf("dialing", "waiting", "active")

/** Restore missing core statuses without discarding edits to existing ones. */
fun CallFlowConfiguration.withRequiredCallStatuses(previous: CallFlowConfiguration = CallFlowConfiguration()): CallFlowConfiguration {
    return copy(
        statuses = keepRequiredStatuses(statuses, previous.statuses),
    )
}

/** The application owns entry, stage order, and button transitions. */
fun CallFlowConfiguration.withProgrammedCallFlow(): CallFlowConfiguration {
    val defaultStages = defaultCallStatuses().associate { it.id to it.stage }
    fun fixed(statuses: List<CallStatusRule>): List<CallStatusRule> = statuses.map { status ->
        val stage = defaultStages[status.id] ?: status.stage
        val nextId = when (stage) {
            CallStage.DIALING -> "waiting"
            CallStage.WAITING -> "active"
            CallStage.OUTCOME -> "disconnecting"
            CallStage.VOICEMAIL -> "voicemail_recording"
            CallStage.ACTIVE -> "disconnecting"
            CallStage.HOLD -> "active"
            CallStage.DISCONNECTING -> "disconnected"
            CallStage.DISCONNECTED -> null
        }
        status.copy(
            name = if (status.id == REMOTE_HANGUP_STATUS_ID) defaultRemoteHangupRule.name else status.name,
            stage = stage,
            timeoutTargetId = nextId,
            buttons = status.buttons.map { button ->
                val enabled = if (stage == CallStage.VOICEMAIL && status.stage != stage) {
                    defaultButtons(stage).first { it.id == button.id }.enabled
                } else button.enabled
                button.copy(targetStatusId = null, enabled = enabled)
            },
        )
    }
    return copy(
        initialStatusId = "dialing",
        statuses = fixed(statuses),
    )
}

private fun keepRequiredStatuses(statuses: List<CallStatusRule>, previous: List<CallStatusRule>): List<CallStatusRule> {
    val proposedById = statuses.associateBy(CallStatusRule::id)
    val previousById = previous.associateBy(CallStatusRule::id)
    val defaults = defaultCallStatuses()
    val defaultsById = defaults.associateBy(CallStatusRule::id)
    val required = defaults.map { fallback ->
        (proposedById[fallback.id] ?: previousById[fallback.id] ?: fallback).withVisibleCallButtons()
    }
    val other = statuses.filterNot { it.id in defaultsById }.distinctBy(CallStatusRule::id).map(CallStatusRule::withVisibleCallButtons)
    return required + other
}

fun CallStatusRule.withVisibleCallButtons(): CallStatusRule {
    val configured = buttons.associateBy(CallButtonRule::id)
    val defaults = defaultButtons(stage).associateBy(CallButtonRule::id)
    return copy(buttons = CallButtonId.entries.map { id -> (configured[id] ?: defaults.getValue(id)).copy(visible = true) })
}

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
    val screenVariant: CallScreenVariant,
    val waitingMediaId: String? = null,
)

fun defaultButtons(stage: CallStage): List<CallButtonRule> {
    val active = stage == CallStage.ACTIVE || stage == CallStage.HOLD || stage == CallStage.VOICEMAIL
    return CallButtonId.entries.map { id ->
        CallButtonRule(id = id, visible = true, enabled = when (id) {
            CallButtonId.MUTE -> active
            CallButtonId.KEYPAD -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
            CallButtonId.SPEAKER -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
            CallButtonId.RECORD -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
            CallButtonId.ADD_CALL -> active
            CallButtonId.AI_CALL_ASSISTANT -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
            CallButtonId.HOLD, CallButtonId.VIDEO_CALL -> active
            CallButtonId.HANGUP -> stage !in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
        })
    }
}

internal fun previousDefaultCallStatuses(): List<CallStatusRule> = listOf(
    CallStatusRule("dialing", "正在呼叫", CallStage.DIALING, 1, "waiting"),
    CallStatusRule("waiting", "等待接听", CallStage.WAITING, 30, "active"),
    CallStatusRule("no_answer", "无人接听", CallStage.OUTCOME, 20, "disconnecting", outcome = "NO_ANSWER"),
    CallStatusRule("busy", "对方忙线", CallStage.OUTCOME, 5, "disconnecting", outcome = "BUSY"),
    CallStatusRule("empty_number", "空号", CallStage.OUTCOME, 10, "disconnecting", outcome = "EMPTY_NUMBER"),
    CallStatusRule("powered_off", "对方已关机", CallStage.OUTCOME, 10, "disconnecting", outcome = "POWERED_OFF"),
    CallStatusRule("unreachable", "无法接通", CallStage.OUTCOME, 10, "disconnecting", outcome = "UNREACHABLE"),
    CallStatusRule("voicemail", "语音留言", CallStage.OUTCOME, 0, "disconnecting", audioPlayCount = 1),
    CallStatusRule("active", "通话中", CallStage.ACTIVE),
    CallStatusRule("hold", "保持", CallStage.HOLD),
    CallStatusRule("disconnecting", "正在挂断", CallStage.DISCONNECTING, 1, "disconnected"),
    CallStatusRule("disconnected", "已挂断", CallStage.DISCONNECTED, 1),
).map { it.withDefaultStatusAudio() }

fun CallStatusRule.withDefaultStatusAudio(): CallStatusRule {
    if (id !in setOf("busy", "no_answer", "powered_off", "unreachable", "voicemail")) return this
    return if (mediaEntryId == null && soundEntryId == null && !soundMuted && !playSoundOnce)
        copy(mediaEntryId = "builtin_$id") else this
}

internal const val DEFAULT_WAITING_SECONDS = 8
internal const val REMOTE_HANGUP_STATUS_ID = "remote_hangup"

internal val defaultRemoteHangupRule = CallStatusRule(
    id = REMOTE_HANGUP_STATUS_ID,
    name = "通话结束",
    stage = CallStage.DISCONNECTING,
    durationSeconds = 1,
    timeoutTargetId = "disconnected",
    soundEntryId = "builtin_legacy_hangup",
    playSoundOnce = true,
)

fun defaultCallStatuses(): List<CallStatusRule> = previousDefaultCallStatuses().map { status ->
    when (status.id) {
        "dialing" -> status.copy(name = "正在拨号", durationSeconds = 2)
        "waiting" -> status.copy(durationSeconds = DEFAULT_WAITING_SECONDS, useDefaultPromptWait = true)
        "no_answer", "busy", "empty_number", "powered_off", "unreachable" -> status.copy(
            durationSeconds = 0,
            audioPlayCount = 4,
            mediaEntryId = when (status.id) {
                "unreachable" -> "builtin_unreachable"
                else -> "builtin_legacy_${status.id}"
            },
        )
        "disconnecting" -> status.copy(playSoundOnce = true, soundEntryId = "builtin_legacy_hangup")
        "disconnected" -> status.copy(durationSeconds = 0)
        "voicemail" -> status.copy(stage = CallStage.VOICEMAIL, timeoutTargetId = "voicemail_recording", buttons = defaultButtons(CallStage.VOICEMAIL))
        else -> status
    }
}.toMutableList().apply {
    add(indexOfFirst { it.id == "empty_number" }, CallStatusRule(
        "in_call", "正在通话中", CallStage.OUTCOME, timeoutTargetId = "disconnecting", outcome = "IN_CALL",
        mediaEntryId = "builtin_busy", audioPlayCount = 4,
    ))
    add(indexOfFirst { it.id == "active" }, CallStatusRule(
        "voicemail_recording", "留言中", CallStage.ACTIVE, timeoutTargetId = "disconnecting",
    ))
    add(indexOfFirst { it.id == "disconnected" }, defaultRemoteHangupRule)
}

internal fun mergeLegacyCallStatuses(
    withoutAvatar: List<CallStatusRule>,
    withAvatar: List<CallStatusRule>,
): List<CallStatusRule> {
    val oldDefaults = CallFlowConfiguration(statuses = previousDefaultCallStatuses())
        .withProgrammedCallFlow().statuses.associateBy(CallStatusRule::id)
    val plain = withoutAvatar.associateBy(CallStatusRule::id)
    val avatar = withAvatar.associateBy(CallStatusRule::id)
    val defaults = defaultCallStatuses()
    val builtInIds = defaults.mapTo(mutableSetOf(), CallStatusRule::id)
    val merged = defaults.map { default ->
        val old = oldDefaults[default.id]
        plain[default.id]?.takeUnless { it == old }
            ?: avatar[default.id]?.takeUnless { it == old }
            ?: default
    }
    return merged + (withoutAvatar + withAvatar).filterNot { it.id in builtInIds }.distinctBy(CallStatusRule::id)
}
