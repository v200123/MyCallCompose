package com.lastcoffee.top.oppo_call_compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRulesTemplateTest {
    @Test fun coreStatusEditsSurviveSave() {
        val previous = CallFlowConfiguration()
        val changed = previous.statuses.first { it.id == "waiting" }.copy(name = "我的等待", durationSeconds = 8, audioPlayCount = 2)
        val proposed = previous.copy(statuses = previous.statuses.map { if (it.id == "waiting") changed else it })

        val result = proposed.withRequiredCallStatuses(previous)

        assertEquals(changed, result.statuses.first { it.id == "waiting" })
        assertEquals(3, result.statuses.count { it.id in coreCallStatusIds })
    }

    @Test fun hiddenAndMissingButtonsAlwaysOccupyTheirNineSlots() {
        val waiting = CallStatusRule("copy", "等待副本", CallStage.WAITING,
            buttons = listOf(CallButtonRule(CallButtonId.RECORD, visible = false, enabled = false)))

        val result = waiting.withVisibleCallButtons()

        assertEquals(CallButtonId.entries, result.buttons.map { it.id })
        assertTrue(result.buttons.all { it.visible })
        assertFalse(result.buttons.first { it.id == CallButtonId.RECORD }.enabled)
        assertTrue(result.buttons.first { it.id == CallButtonId.HANGUP }.enabled)
    }

    @Test fun missingCoreStatusesAreRestored() {
        val custom = CallStatusRule("custom", "自定义", CallStage.WAITING)
        val result = CallFlowConfiguration(statuses = listOf(custom)).withRequiredCallStatuses(
            CallFlowConfiguration(statuses = listOf(custom)))

        assertEquals(defaultCallStatuses().map { it.id } + "custom", result.statuses.map { it.id })
    }

    @Test fun existingRulesGetASeparateRemoteHangupDefault() {
        val existing = CallFlowConfiguration(statuses = defaultCallStatuses()
            .filterNot { it.id == REMOTE_HANGUP_STATUS_ID }
            .map { if (it.id == "disconnecting") it.copy(durationSeconds = 6) else it })
        val updated = existing.withRequiredCallStatuses(existing).withProgrammedCallFlow()

        assertEquals(defaultRemoteHangupRule, updated.statuses.first { it.id == REMOTE_HANGUP_STATUS_ID })
        assertEquals(6, updated.statuses.first { it.id == "disconnecting" }.durationSeconds)
    }

    @Test fun legacyRulesMergeBothLayoutsAndRefreshUntouchedDefaults() {
        val old = CallFlowConfiguration(statuses = previousDefaultCallStatuses()).withProgrammedCallFlow().statuses
        val waiting = old.first { it.id == "waiting" }.copy(durationSeconds = 15)
        val active = old.first { it.id == "active" }.copy(name = "客服通话中")
        val extra = CallStatusRule("avatar-custom", "旧头像自定义", CallStage.OUTCOME)
        val plain = old.map { if (it.id == "waiting") waiting else it }
        val avatar = old.map {
            when (it.id) {
                "waiting" -> waiting.copy(durationSeconds = 20)
                "active" -> active
                else -> it
            }
        } + extra

        val merged = mergeLegacyCallStatuses(plain, avatar).associateBy { it.id }
        assertEquals(waiting, merged["waiting"])
        assertEquals(active, merged["active"])
        assertEquals(extra, merged[extra.id])
        assertEquals(2, merged.getValue("dialing").durationSeconds)
        assertEquals(4, merged.getValue("unreachable").audioPlayCount)
        assertEquals(8, mergeLegacyCallStatuses(old, old).first { it.id == "waiting" }.durationSeconds)
    }

    @Test fun programmedFlowDiscardsLegacyEntryAndJumpChoices() {
        val old = CallFlowConfiguration(
            initialStatusId = "busy",
            statuses = defaultCallStatuses().map { status ->
                if (status.id == "waiting") status.copy(name = "自定义等待", durationSeconds = 7, timeoutTargetId = "busy",
                    buttons = status.buttons.map { it.copy(targetStatusId = "active") }) else status
            },
        )

        val fixed = old.withProgrammedCallFlow()
        val waiting = fixed.statuses.first { it.id == "waiting" }

        assertEquals("dialing", fixed.initialStatusId)
        assertEquals("active", waiting.timeoutTargetId)
        assertEquals("自定义等待", waiting.name)
        assertEquals(7, waiting.durationSeconds)
        assertTrue(waiting.buttons.all { it.targetStatusId == null })
        assertEquals("disconnecting", fixed.statuses.first { it.id == "no_answer" }.timeoutTargetId)
    }

    @Test fun serviceNumberUsesAvatarLayoutByDefault() {
        assertEquals(CallScreenVariant.WITH_AVATAR, CallScreenVariant.forNumber("10086"))
        assertEquals(CallScreenVariant.WITHOUT_AVATAR, CallScreenVariant.forNumber("136 5817 0135"))
    }
}
