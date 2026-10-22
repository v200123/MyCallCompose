package com.lastcoffee.top.oppo_call_compose.feature.home

import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneHomeLogicTest {
    private fun call(number: String, time: Long, kind: CallKind = CallKind.INCOMING) =
        CallRecord(number, "", "2026/10/22", kind, timeMillis = time)

    @Test fun onlyConsecutiveEqualNumbersAreGrouped() {
        val groups = groupHomeCalls(listOf(call("136 5817 0135", 5), call("13658170135", 4), call("13900000000", 3), call("13658170135", 2)))
        assertEquals(listOf(2, 1, 1), groups.map { it.calls.size })
    }
    @Test fun missedFilterExcludesRejectedAndUnconnectedOutgoingCalls() {
        val missed = call("1", 3, CallKind.MISSED)
        assertEquals(listOf(missed), groupHomeCalls(listOf(missed, call("2", 2, CallKind.BLOCKED), call("3", 1, CallKind.OUTGOING).copy(isUnanswered = true)), true).map { it.latest })
    }
    @Test fun latestCallDeterminesWhetherGroupIsMissed() {
        assertTrue(groupHomeCalls(listOf(call("1", 2), call("1", 1, CallKind.MISSED)), true).isEmpty())
    }
    @Test fun dialMatchingFindsMiddleDigitsAndIgnoresNumberSpaces() {
        val contact = ContactMatch("川哥", "191 3606 6659", "四川成都")
        assertEquals(listOf(contact), matchingDialContacts(listOf(contact), "136"))
        assertTrue(matchingDialContacts(listOf(contact), "").isEmpty())
    }
    @Test fun relativeDateShowsTodayTimeYesterdayAndOlderDate() {
        val day = LocalDate.of(2026, 10, 22)
        val time = day.atTime(13, 22).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("13:22", homeCallDate(call("1", time), day))
        assertEquals("昨天", homeCallDate(call("1", time), day.plusDays(1)))
        assertEquals("10月22日", homeCallDate(call("1", time), day.plusDays(2)))
    }
    @Test fun attributionFallsBackToSavedCarrierAndCustomLocationWins() {
        val record = call("1", 1).copy(attribution = "四川德阳", operator = "移动")
        assertEquals("四川德阳 移动", record.homeAttribution(null))
        assertEquals("北京 联通", record.homeAttribution("北京 联通"))
    }
}
