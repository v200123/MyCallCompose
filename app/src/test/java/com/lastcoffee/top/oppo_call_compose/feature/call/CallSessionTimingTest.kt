package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.model.CallStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallSessionTimingTest {
    @Test
    fun unansweredCallHasNoConnectedDuration() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.DIALING, 0L)
        timing.enterStage(CallStage.WAITING, 1_000L)
        timing.enterStage(CallStage.DISCONNECTED, 31_000L)

        assertNull(timing.connectedAtElapsedMillis)
        assertEquals(0, timing.elapsedSeconds(40_000L))
        assertEquals(0, timing.durationSeconds(40_000L))
    }

    @Test
    fun connectionAtZeroIsValidAndShortConnectedCallSavesOneSecond() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.ACTIVE, 0L)
        timing.enterStage(CallStage.DISCONNECTING, 350L)

        assertEquals(0L, timing.connectedAtElapsedMillis)
        assertEquals(0, timing.elapsedSeconds(10_000L))
        assertEquals(1, timing.durationSeconds(10_000L))
    }

    @Test
    fun holdingAndReturningActiveKeepTheFirstConnectionTime() {
        val timing = CallSessionTiming(100L)
        timing.enterStage(CallStage.ACTIVE, 1_100L)
        timing.enterStage(CallStage.HOLD, 3_100L)
        assertEquals(6, timing.elapsedSeconds(7_100L))

        timing.enterStage(CallStage.ACTIVE, 7_600L)

        assertEquals(1_100L, timing.connectedAtElapsedMillis)
        assertEquals(9, timing.elapsedSeconds(10_100L))
    }

    @Test
    fun holdAsInitialStageAlsoStartsTheConnectionClock() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.HOLD, 2_000L)
        timing.enterStage(CallStage.ACTIVE, 3_000L)

        assertEquals(2_000L, timing.connectedAtElapsedMillis)
        assertEquals(3, timing.durationSeconds(5_500L))
    }

    @Test
    fun directCustomTransitionToDisconnectedFreezesConnectedDuration() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.ACTIVE, 2_000L)
        timing.enterStage(CallStage.DISCONNECTED, 7_900L)

        assertEquals(5, timing.durationSeconds(20_000L))
        assertEquals(5, timing.elapsedSeconds(20_000L))
    }

    @Test
    fun disconnectingDelayAndFurtherTransitionsDoNotExtendTheSavedDuration() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.ACTIVE, 1_000L)
        timing.enterStage(CallStage.DISCONNECTING, 5_500L)
        timing.enterStage(CallStage.DISCONNECTED, 6_500L)
        timing.enterStage(CallStage.ACTIVE, 9_000L)

        assertEquals(4, timing.durationSeconds(30_000L))
        assertEquals(1_000L, timing.connectedAtElapsedMillis)
    }

    @Test
    fun backgroundTimeDoesNotRestartTheStatusDeadlineOrConnectedDuration() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.WAITING, 1_000L)

        assertEquals(28_000L, timing.remainingStatusMillis(30, 3_000L))
        // No lifecycle callback or ticking is necessary during a long pause.
        assertEquals(3_000L, timing.remainingStatusMillis(30, 28_000L))
        assertEquals(0L, timing.remainingStatusMillis(30, 35_000L))

        timing.enterStage(CallStage.ACTIVE, 40_000L)
        assertEquals(60, timing.elapsedSeconds(100_000L))
    }

    @Test
    fun anotherStatusInTheSameStageGetsItsOwnDeadline() {
        val timing = CallSessionTiming(0L)
        timing.enterStage(CallStage.OUTCOME, 1_000L)
        assertEquals(2_000L, timing.remainingStatusMillis(5, 4_000L))

        timing.enterStage(CallStage.OUTCOME, 4_000L)

        assertEquals(4_000L, timing.statusEnteredAtElapsedMillis)
        assertEquals(4_000L, timing.remainingStatusMillis(5, 5_000L))
    }

    @Test
    fun zeroOrNegativeStatusDurationHasNoRemainingDelay() {
        val timing = CallSessionTiming(5_000L)

        assertEquals(0L, timing.remainingStatusMillis(0, 5_000L))
        assertEquals(0L, timing.remainingStatusMillis(-1, 5_000L))
    }
}
