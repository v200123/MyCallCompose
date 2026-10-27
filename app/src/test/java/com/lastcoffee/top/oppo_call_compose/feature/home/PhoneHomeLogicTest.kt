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

    @Test fun equalNumbersAreGroupedEvenWhenOtherCallsSeparateThem() {
        val groups = groupHomeCalls(listOf(call("139 0000 0001", 5), call("13900000001", 4), call("13900000002", 3), call("13900000001", 2)))
        assertEquals(listOf(3, 1), groups.map { it.calls.size })
        assertEquals(listOf(5L, 4L, 2L), groups.first().calls.map { it.timeMillis })
    }
    @Test fun missedFilterExcludesRejectedAndUnconnectedOutgoingCalls() {
        val missed = call("1", 3, CallKind.MISSED)
        assertEquals(listOf(missed), groupHomeCalls(listOf(missed, call("2", 2, CallKind.BLOCKED), call("3", 1, CallKind.OUTGOING).copy(isUnanswered = true)), true).map { it.latest })
    }
    @Test fun missedFilterKeepsOlderMissedCallsForTheSameNumber() {
        val missed = call("1", 1, CallKind.MISSED)
        assertEquals(listOf(missed), groupHomeCalls(listOf(call("1", 2), missed), true).single().calls)
    }
    @Test fun dialMatchingFindsMiddleDigitsAndIgnoresNumberSpaces() {
        val contact = ContactMatch("测试联系人", "139 0000 0001", "测试地区")
        assertEquals(listOf(contact), matchingDialContacts(listOf(contact), "000"))
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
        assertEquals("四川德阳 移动", record.copy(attribution = "四川 德阳").homeAttribution(null))
        assertEquals("北京 联通", record.homeAttribution("北京 联通"))
    }
    @Test fun callTypeIsNotUsedAsAttribution() {
        val record = call("1368170135", 1, CallKind.OUTGOING).copy(description = "呼出  0秒")
        assertEquals("未知归属地", record.homeAttribution(null))
    }
}
