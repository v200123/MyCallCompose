package com.lastcoffee.top.oppo_call_compose.ui.common

import androidx.compose.ui.graphics.Color
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallIndicatorColorTest {
    @Test
    fun onlyMissedIncomingCallsUseRed() {
        val outgoing = ManualCall(
            number = "10086",
            kind = CallKind.OUTGOING,
            durationSeconds = 0,
            timeMillis = 0,
            wasConnected = false,
        ).toCallRecord()
        assertTrue(outgoing.isUnanswered)
        assertEquals(Color(0xFF757575), outgoing.kind.indicatorColor)
        assertEquals(Color(0xFF757575), CallKind.INCOMING.indicatorColor)
        assertEquals(Color(0xFF757575), CallKind.BLOCKED.indicatorColor)
        assertEquals(MissedRed, CallKind.MISSED.indicatorColor)
    }
}
