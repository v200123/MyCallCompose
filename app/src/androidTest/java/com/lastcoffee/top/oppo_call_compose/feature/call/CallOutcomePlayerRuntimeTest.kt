package com.lastcoffee.top.oppo_call_compose.feature.call

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallOutcomePlayerRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun busyToneCompletesOnceOnTheMainThread() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Main)
        val finished = CompletableDeferred<Unit>()
        val cycles = mutableListOf<Int>()
        var completions = 0
        try {
            val player = withContext(Dispatchers.Main) {
                CallOutcomePlayer(context, CallOutcome.BUSY, scope,
                    onCycle = {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        cycles += it
                    },
                    onFinished = {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        completions++
                        finished.complete(Unit)
                    },
                )
            }
            withTimeout(8_000) { finished.await() }
            withContext(Dispatchers.Main) {
                player.stop()
                player.stop()
                assertEquals(listOf(1), cycles)
                assertEquals(1, completions)
            }
        } finally {
            owner.cancelAndJoin()
        }
    }

    @Test fun stoppingOrCancellingTheOwnerPreventsLateCompletion() = runBlocking {
        val owners = List(2) { SupervisorJob() }
        val started = List(2) { CompletableDeferred<Unit>() }
        val cycles = IntArray(2)
        val completions = IntArray(2)
        try {
            val players = withContext(Dispatchers.Main) {
                owners.mapIndexed { index, owner ->
                    CallOutcomePlayer(context, CallOutcome.BUSY, CoroutineScope(owner + Dispatchers.Main),
                        onCycle = { cycles[index]++; started[index].complete(Unit) },
                        onFinished = { completions[index]++ },
                    )
                }
            }
            withTimeout(3_000) { started.forEach { it.await() } }
            withContext(Dispatchers.Main) {
                players[0].stop()
                players[0].stop()
                owners[1].cancel()
            }
            delay(5_500)
            withContext(Dispatchers.Main) {
                assertArrayEquals(intArrayOf(1, 1), cycles)
                assertArrayEquals(intArrayOf(0, 0), completions)
            }
        } finally {
            owners.forEach { it.cancelAndJoin() }
        }
    }

    @Test fun speechOrItsFallbackCompletesOnTheMainThread() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Main)
        val finished = CompletableDeferred<Unit>()
        var completions = 0
        try {
            withContext(Dispatchers.Main) {
                CallOutcomePlayer(context, CallOutcome.EMPTY_NUMBER, scope,
                    onCycle = {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        assertEquals(1, it)
                    },
                    onFinished = {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        completions++
                        finished.complete(Unit)
                    },
                )
            }
            withTimeout(15_000) { finished.await() }
            withContext(Dispatchers.Main) { assertEquals(1, completions) }
        } finally {
            owner.cancelAndJoin()
        }
    }
}
