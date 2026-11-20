package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.withProgrammedCallFlow
import org.junit.Assert.*
import org.junit.Test

class PromptRingingTest {
    private fun runner(type: CallPromptType, mode: CallAudioMode) = CallRuleExecutor(
        CallFlowConfiguration().withProgrammedCallFlow(), "dialing", 0,
        promptStatusId = type.legacyStatusId, promptRinging = promptRinging(type, mode),
    )

    @Test fun unansweredToneRingsFifteenTimesBeforeThePrompt() {
        val call = runner(CallPromptType.NO_ANSWER, CallAudioMode.DEFAULT_TONE)
        assertTrue(call.advanceIfDue(0, 2_000))
        assertEquals("waiting", call.step.rule.id)
        assertEquals(15, call.step.rule.audioPlayCount)
        assertNull(call.remainingMillis(90_000))
        assertTrue(call.audioFinished(1, 40_000))
        assertEquals("no_answer", call.step.rule.id)
        assertEquals(4, call.step.rule.audioPlayCount)
    }

    @Test fun unansweredVideoAndMusicFinishOnePlayRegardlessOfLength() {
        for (mode in listOf(CallAudioMode.RINGBACK, CallAudioMode.MUSIC)) {
            val call = runner(CallPromptType.NO_ANSWER, mode)
            assertTrue(call.advanceIfDue(0, 2_000))
            assertEquals(1, call.step.rule.audioPlayCount)
            assertNull(call.remainingMillis(300_000))
            assertTrue(call.audioFinished(1, 125_000))
            assertEquals("no_answer", call.step.rule.id)
        }
    }

    @Test fun busyToneRingsFourTimesAndMusicAndVideoUseFourSeconds() {
        val tone = runner(CallPromptType.BUSY, CallAudioMode.DEFAULT_TONE)
        assertTrue(tone.advanceIfDue(0, 2_000))
        assertEquals(4, tone.step.rule.audioPlayCount)
        assertNull(tone.remainingMillis(7_000))
        assertTrue(tone.audioFinished(1, 11_000))
        assertEquals("busy", tone.step.rule.id)

        for (mode in listOf(CallAudioMode.MUSIC, CallAudioMode.RINGBACK)) {
            val call = runner(CallPromptType.BUSY, mode)
            assertTrue(call.advanceIfDue(0, 2_000))
            assertEquals(0, call.step.rule.audioPlayCount)
            assertFalse(call.audioFinished(1, 3_000))
            assertFalse(call.advanceIfDue(1, 5_999))
            assertTrue(call.advanceIfDue(1, 6_000))
            assertEquals("busy", call.step.rule.id)
        }
    }

    @Test fun otherPromptsSkipTheRingingStageInEveryMode() {
        for (type in listOf(CallPromptType.POWER_OFF, CallPromptType.EMPTY_NUMBER, CallPromptType.IN_CALL, CallPromptType.UNREACHABLE)) {
            for (mode in CallAudioMode.entries.filter { it.isCallMode }) {
                val call = runner(type, mode)
                assertTrue(call.advanceIfDue(0, 2_000))
                assertEquals(type.legacyStatusId, call.step.rule.id)
                assertNull(call.timing.connectedAtElapsedMillis)
            }
        }
    }

    @Test fun voicemailConnectsDuringTheGreetingAndStaysConnectedForTheMessage() {
        val call = runner(CallPromptType.VOICEMAIL, CallAudioMode.MUSIC)
        assertTrue(call.advanceIfDue(0, 2_000))
        assertEquals("voicemail", call.step.rule.id)
        assertEquals(2_000L, call.timing.connectedAtElapsedMillis)
        assertTrue(call.audioFinished(1, 10_000))
        assertEquals("voicemail_recording", call.step.rule.id)
        assertFalse(call.audioFinished(2, 15_000))
        assertEquals(13, call.timing.elapsedSeconds(15_000))
        assertTrue(call.hangup(15_000, fromRemote = true))
        assertEquals(13, call.timing.durationSeconds(99_000))
        assertFalse(call.audioFinished(1, 99_000))
    }
}
