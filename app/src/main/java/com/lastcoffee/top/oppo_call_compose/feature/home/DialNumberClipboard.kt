package com.lastcoffee.top.oppo_call_compose.feature.home

import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class ClipboardDialNumber(val number: String, val copiedAt: Long)

@Composable
internal fun rememberClipboardDialNumber(clipboard: ClipboardManager?, enabled: Boolean): ClipboardDialNumber? {
    if (!enabled || clipboard == null) return null

    var number by remember(clipboard) { mutableStateOf<ClipboardDialNumber?>(null) }
    // Read immediately on entry, then keep the visible suggestion in sync with the clipboard.
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            number = clipboard.readDialNumber()
        }
        clipboard.addPrimaryClipChangedListener(listener)
        listener.onPrimaryClipChanged()
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }
    return number
}

internal fun ClipboardManager.readDialNumber(): ClipboardDialNumber? {
    val clip = primaryClip ?: return null
    val item = clip.getItemAt(0)
    val text = item.text?.toString()
        ?: item.uri?.takeIf { it.scheme.equals("tel", ignoreCase = true) }?.toString()
    val number = text?.let(::qualifiedClipboardDialNumber) ?: return null
    return ClipboardDialNumber(number, clip.description.timestamp)
}
