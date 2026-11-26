package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.model.CallStage

/** Session timestamps from one monotonic clock, independent of UI lifecycle. */
internal class CallSessionTiming(val startedAtElapsedMillis: Long) {
    var connectedAtElapsedMillis: Long? = null
        private set

    var statusEnteredAtElapsedMillis: Long = startedAtElapsedMillis
        private set

    private var endedAtElapsedMillis: Long? = null

    /** Call once on each status entry, including transitions between statuses of the same stage. */
    fun enterStage(stage: CallStage, now: Long) {
        statusEnteredAtElapsedMillis = now
        if (endedAtElapsedMillis != null) return

        when (stage) {
            CallStage.ACTIVE, CallStage.HOLD, CallStage.VOICEMAIL -> {
                if (connectedAtElapsedMillis == null) connectedAtElapsedMillis = now
            }
            CallStage.DISCONNECTING, CallStage.DISCONNECTED -> endedAtElapsedMillis = now
            else -> Unit
        }
    }

    /** Displayed duration; holding and background time remain part of the connected interval. */
    fun elapsedSeconds(now: Long): Int {
        val connectedAt = connectedAtElapsedMillis ?: return 0
        return secondsBetween(connectedAt, endedAtElapsedMillis ?: now)
    }

    /** A timer shown before connection measures the time spent in the current status. */
    fun statusDisplaySeconds(now: Long): Int =
        if (connectedAtElapsedMillis == null) secondsBetween(statusEnteredAtElapsedMillis, now) else elapsedSeconds(now)

    private fun secondsBetween(start: Long, end: Long): Int =
        ((end - start).coerceAtLeast(0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Persisted duration distinguishes a short connected call from an unanswered call. */
    fun durationSeconds(now: Long): Int =
        if (connectedAtElapsedMillis == null) 0 else elapsedSeconds(now).coerceAtLeast(1)

    /** Recomputing after a pause preserves the original deadline instead of restarting its timer. */
    fun remainingStatusMillis(durationSeconds: Int, now: Long): Long {
        val durationMillis = durationSeconds.coerceAtLeast(0).toLong() * 1_000L
        val timeInStatus = (now - statusEnteredAtElapsedMillis).coerceAtLeast(0L)
        return (durationMillis - timeInStatus).coerceAtLeast(0L)
    }
}
