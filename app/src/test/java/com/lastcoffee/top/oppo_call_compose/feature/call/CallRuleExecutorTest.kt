package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.withProgrammedCallFlow
import org.junit.Assert.*
import org.junit.Test

class CallRuleExecutorTest {
    private fun executor(vararg rules: CallStatusRule) = CallRuleExecutor(
        CallFlowConfiguration(initialStatusId = rules.first().id, statuses = rules.toList()), rules.first().id, 0,
    )

    @Test fun programmedNormalCallStartsDialingThenWaitsThenConnects() {
        val configuration = CallFlowConfiguration().withProgrammedCallFlow()
        val runner = CallRuleExecutor(configuration, configuration.initialStatusId, 0)

        assertEquals("dialing", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(0, 1_000))
        assertEquals("waiting", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(1, 31_000))
        assertEquals("active", runner.step.rule.id)
    }

    @Test fun programmedPromptBranchEndsTheCall() {
        val configuration = CallFlowConfiguration().withProgrammedCallFlow()
        val runner = CallRuleExecutor(configuration, "dialing", 0)

        assertTrue(runner.moveTo("busy", 2_000))
        assertTrue(runner.advanceIfDue(1, 7_000))
        assertEquals("disconnecting", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(2, 8_000))
        assertEquals("disconnected", runner.step.rule.id)
    }

    @Test fun completeCallRunsThroughConfiguredStepsWithoutButtonInput() {
        val runner = executor(
            CallStatusRule("dial", "呼叫", CallStage.DIALING, 1, "wait"),
            CallStatusRule("wait", "等待", CallStage.WAITING, 2, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE, 3, "ending"),
            CallStatusRule("ending", "挂断", CallStage.DISCONNECTING, 1, "end"),
            CallStatusRule("end", "结束", CallStage.DISCONNECTED, 1),
        )
        assertFalse(runner.advanceIfDue(0, 999))
        assertTrue(runner.advanceIfDue(0, 1_000))
        assertEquals("wait", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(1, 3_000))
        assertEquals("talk", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(2, 6_000))
        assertTrue(runner.advanceIfDue(3, 7_000))
        assertFalse(runner.claimCompletion(4, 7_999))
        assertTrue(runner.claimCompletion(4, 8_000))
        assertEquals(3, runner.timing.durationSeconds(8_000))
        assertFalse(runner.claimCompletion(4, 9_000))
    }

    @Test fun hangupInvalidatesThePendingAnswerAndMediaCallback() {
        val runner = executor(
            CallStatusRule("wait", "等待", CallStage.WAITING, 5, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
        )
        assertTrue(runner.hangup(2_000))
        assertFalse(runner.advanceIfDue(0, 5_000))
        assertFalse(runner.audioFinished(0, 5_000))
        assertFalse(runner.moveTo("talk", 5_000, 0))
        assertEquals(CallStage.DISCONNECTING, runner.step.rule.stage)
        assertEquals(0, runner.timing.durationSeconds(5_000))
    }

    @Test fun operationBranchReplacesTheOldDeadline() {
        val runner = executor(
            CallStatusRule("wait", "等待", CallStage.WAITING, 5, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
            CallStatusRule("busy", "忙线", CallStage.OUTCOME, 3, "talk"),
        )
        assertTrue(runner.moveTo("busy", 4_000))
        assertFalse(runner.advanceIfDue(0, 5_000))
        assertEquals(2_000L, runner.remainingMillis(5_000))
        assertTrue(runner.advanceIfDue(1, 7_000))
    }

    @Test fun returningToTheSameRuleCreatesANewTimerEntry() {
        val runner = executor(CallStatusRule("wait", "等待", CallStage.WAITING, 5, "wait"))
        assertTrue(runner.moveTo("wait", 4_000))
        assertFalse(runner.advanceIfDue(0, 5_000))
        assertEquals(4_000L, runner.remainingMillis(5_000))
        assertTrue(runner.advanceIfDue(1, 9_000))
        assertEquals(2L, runner.step.revision)
        assertEquals(5_000L, runner.remainingMillis(9_000))
    }

    @Test fun timedAudioCannotHangUpOrAdvanceBeforeTheRuleDeadline() {
        val runner = executor(
            CallStatusRule("audio", "提示", CallStage.OUTCOME, 10, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
        )
        assertFalse(runner.audioFinished(0, 1_000))
        assertEquals("audio", runner.step.rule.id)
        assertTrue(runner.advanceIfDue(0, 10_000))
        assertEquals("talk", runner.step.rule.id)
    }

    @Test fun untimedAudioAdvancesOnlyToItsConfiguredTargetAfterPlayback() {
        val runner = executor(
            CallStatusRule("audio", "提示", CallStage.OUTCOME, 0, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
        )
        assertNull(runner.remainingMillis(1_000))
        assertTrue(runner.audioFinished(0, 2_000))
        assertEquals("talk", runner.step.rule.id)
        assertFalse(runner.audioFinished(0, 3_000))
    }

    @Test fun finishingMusicDoesNotEndAnUntimedActiveCall() {
        val runner = executor(
            CallStatusRule("active", "通话中", CallStage.ACTIVE, 0, "disconnecting", audioPlayCount = 2),
            CallStatusRule("disconnecting", "挂断", CallStage.DISCONNECTING, 1, "disconnected"),
        )

        assertFalse(runner.audioFinished(0, 5_000))
        assertEquals("active", runner.step.rule.id)
    }

    @Test fun explicitStayDoesNotInventANextStepWhenAudioEnds() {
        val runner = executor(CallStatusRule("audio", "提示", CallStage.OUTCOME, 5))
        assertNull(runner.remainingMillis(100_000))
        assertFalse(runner.advanceIfDue(0, 100_000))
        assertFalse(runner.audioFinished(0, 100_000))
        assertEquals("audio", runner.step.rule.id)
    }

    @Test fun missingTerminalRulesStillFinishAndPreserveDuration() {
        val runner = executor(CallStatusRule("talk", "接通", CallStage.ACTIVE))
        assertTrue(runner.hangup(2_500))
        assertTrue(runner.advanceIfDue(1, 3_500))
        assertEquals(CallStage.DISCONNECTED, runner.step.rule.stage)
        assertTrue(runner.claimCompletion(2, 4_500))
        assertEquals(2, runner.timing.durationSeconds(4_500))
    }

    @Test fun terminalStepRejectsLateOperationsEvenWithCurrentRevision() {
        val runner = executor(
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
            CallStatusRule("end", "结束", CallStage.DISCONNECTED),
        )
        assertTrue(runner.moveTo("end", 2_000))
        assertFalse(runner.moveTo("talk", 2_001))
        assertFalse(runner.hangup(2_001))
        assertTrue(runner.claimCompletion(1, 3_000))
    }

    @Test fun backgroundTimeDoesNotRestartTheEntryDeadline() {
        val runner = executor(
            CallStatusRule("wait", "等待", CallStage.WAITING, 5, "talk"),
            CallStatusRule("talk", "接通", CallStage.ACTIVE),
        )
        assertEquals(4_000L, runner.remainingMillis(1_000))
        assertEquals(0L, runner.remainingMillis(10_000))
        assertTrue(runner.advanceIfDue(0, 10_000))
    }

    @Test fun invalidTargetDoesNotCancelTheCurrentRule() {
        val runner = executor(CallStatusRule("wait", "等待", CallStage.WAITING))
        assertFalse(runner.moveTo("missing", 2_000))
        assertEquals(0L, runner.step.revision)
        assertEquals(0L, runner.timing.statusEnteredAtElapsedMillis)
    }

    @Test fun emptyConfigurationUsesRunnableDefaults() {
        val runner = CallRuleExecutor(CallFlowConfiguration(statuses = emptyList()), "missing", 0)
        assertEquals(CallStage.DIALING, runner.step.rule.stage)
        assertTrue(runner.advanceIfDue(0, 1_000))
        assertEquals(CallStage.WAITING, runner.step.rule.stage)
    }
}
