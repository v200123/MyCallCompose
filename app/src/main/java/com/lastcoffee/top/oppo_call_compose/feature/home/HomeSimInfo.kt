package com.lastcoffee.top.oppo_call_compose.feature.home

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager

internal fun homeSimLabels(context: Context, dualSimDisplayEnabled: Boolean): List<String> = runCatching {
    if (!dualSimDisplayEnabled) return emptyList()
    val manager = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
    val count = if (Build.VERSION.SDK_INT >= 30) manager.activeModemCount else manager.phoneCount
    val slots = (0 until count).filter { manager.getSimState(it) !in listOf(TelephonyManager.SIM_STATE_ABSENT, TelephonyManager.SIM_STATE_UNKNOWN) }
    val carrier = runCatching { manager.simOperatorName }.getOrNull().orEmpty().ifBlank { "SIM" }
    if (slots.size > 1) slots.map { "$carrier${it + 1}" } else emptyList()
}.getOrDefault(emptyList())
