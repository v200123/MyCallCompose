package com.lastcoffee.top.oppo_call_compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneExtrasLocationTest {
    @Test
    fun homeLocationJoinsProvinceAndCityBeforeCarrier() {
        assertEquals("四川德阳 移动", CustomPhoneLocation("1365817", "四川", "德阳", "移动").displayText)
        assertEquals("北京 移动", formatPhoneLocation("北京", "北京", "移动"))
        assertEquals("四川德阳 移动", normalizeCachedPhoneLocation("四川 德阳 移动"))
    }

    @Test
    fun fullNumberOverridesSevenDigitMobilePrefix() {
        val extras = PhoneExtras(locations = mapOf(
            "1380013" to CustomPhoneLocation("1380013", "北京", "北京", ""),
            "13800138000" to CustomPhoneLocation("13800138000", "上海", "上海", ""),
        ))

        assertEquals("上海", extras.locationFor("13800138000")?.substringBefore(' '))
        assertEquals("北京", extras.locationFor("13800139999")?.substringBefore(' '))
        assertEquals("上海", extras.locationFor("+86 13800138000")?.substringBefore(' '))
        assertNull(extras.locationFor("13800149999"))
        assertEquals(CustomLocationSource.EXACT_NUMBER, extras.customLocationMatch("13800138000")?.source)
        assertEquals(CustomLocationSource.SEVEN_DIGIT_PREFIX, extras.customLocationMatch("13800139999")?.source)
    }

    @Test
    fun sevenDigitPrefixOnlyMatchesMainlandMobileNumbers() {
        val extras = PhoneExtras(locations = mapOf(
            "1380013" to CustomPhoneLocation("1380013", "北京", "", ""),
        ))

        assertEquals("北京", extras.locationFor("0086 13800139999"))
        assertNull(extras.locationFor("1380013" + "123"))
        assertNull(extras.locationFor("0211380013"))
    }
}
