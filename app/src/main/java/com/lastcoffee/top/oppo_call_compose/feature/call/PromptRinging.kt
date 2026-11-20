package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode

/** Ringing before a failed call. A zero duration lets the selected media finish. */
internal data class PromptRinging(
    val durationSeconds: Int = 0,
    val playCount: Int = 1,
    val skipWaiting: Boolean = false,
)

internal fun promptRinging(type: CallPromptType, mode: CallAudioMode): PromptRinging = when (type) {
    CallPromptType.NO_ANSWER -> PromptRinging(playCount = if (mode == CallAudioMode.DEFAULT_TONE) 15 else 1)
    CallPromptType.BUSY -> if (mode == CallAudioMode.DEFAULT_TONE) PromptRinging(playCount = 4)
        else PromptRinging(durationSeconds = 4, playCount = 0)
    else -> PromptRinging(skipWaiting = true)
}
