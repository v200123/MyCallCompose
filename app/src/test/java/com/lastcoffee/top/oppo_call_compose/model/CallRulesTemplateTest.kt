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

    @Test fun avatarAndPlainRulesStayIndependent() {
        val config = CallFlowConfiguration()
        val avatarCopy = config.avatarStatuses.first { it.id == "waiting" }
            .copy(id = "avatar-wait", name = "头像等待", durationSeconds = 8)
        val edited = config.withVariantRules(CallScreenVariant.WITH_AVATAR,
            config.forVariant(CallScreenVariant.WITH_AVATAR).copy(
                initialStatusId = avatarCopy.id,
                statuses = config.avatarStatuses + avatarCopy,
            ))
        val saved = edited.withRequiredCallStatuses(config)

        assertEquals(config.statuses.associateBy { it.id }, saved.statuses.associateBy { it.id })
        assertEquals("dialing", saved.initialStatusId)
        assertEquals("avatar-wait", saved.avatarInitialStatusId)
        assertTrue(saved.avatarStatuses.any { it.id == "avatar-wait" })
        assertFalse(saved.statuses.any { it.id == "avatar-wait" })
    }

    @Test fun avatarCoreStatusCanBeEditedWithoutChangingPlainRules() {
        val previous = CallFlowConfiguration()
        val changed = previous.avatarStatuses.first { it.id == "active" }.copy(name = "客服通话中", durationSeconds = 15)
        val proposed = previous.copy(avatarStatuses = previous.avatarStatuses.map { if (it.id == "active") changed else it })

        val saved = proposed.withRequiredCallStatuses(previous)

        assertEquals(changed, saved.avatarStatuses.first { it.id == "active" })
        assertEquals(previous.statuses.first { it.id == "active" }, saved.statuses.first { it.id == "active" })
    }

    @Test fun programmedFlowDiscardsLegacyEntryAndJumpChoices() {
        val old = CallFlowConfiguration(
            initialStatusId = "busy",
            avatarInitialStatusId = "active",
            statuses = defaultCallStatuses().map { status ->
                if (status.id == "waiting") status.copy(name = "自定义等待", durationSeconds = 7, timeoutTargetId = "busy",
                    buttons = status.buttons.map { it.copy(targetStatusId = "active") }) else status
            },
        )

        val fixed = old.withProgrammedCallFlow()
        val waiting = fixed.statuses.first { it.id == "waiting" }

        assertEquals("dialing", fixed.initialStatusId)
        assertEquals("dialing", fixed.avatarInitialStatusId)
        assertEquals("active", waiting.timeoutTargetId)
        assertEquals("自定义等待", waiting.name)
        assertEquals(7, waiting.durationSeconds)
        assertTrue(waiting.buttons.all { it.targetStatusId == null })
        assertEquals("disconnecting", fixed.statuses.first { it.id == "no_answer" }.timeoutTargetId)
    }

    @Test fun bothRuleSetsKeepRequiredStatusesAndNineButtons() {
        val config = CallFlowConfiguration(
            avatarStatuses = listOf(CallStatusRule("custom-avatar", "头像自定义", CallStage.ACTIVE,
                buttons = listOf(CallButtonRule(CallButtonId.MUTE, visible = false)))),
        ).withRequiredCallStatuses()

        assertEquals(3, config.statuses.count { it.id in coreCallStatusIds })
        assertEquals(3, config.avatarStatuses.count { it.id in coreCallStatusIds })
        assertTrue(config.avatarStatuses.all { rule -> rule.buttons.size == 9 && rule.buttons.all { it.visible } })
    }

    @Test fun serviceNumberUsesAvatarLayoutByDefault() {
        assertEquals(CallScreenVariant.WITH_AVATAR, CallScreenVariant.forNumber("10086"))
        assertEquals(CallScreenVariant.WITHOUT_AVATAR, CallScreenVariant.forNumber("136 5817 0135"))
    }
}
