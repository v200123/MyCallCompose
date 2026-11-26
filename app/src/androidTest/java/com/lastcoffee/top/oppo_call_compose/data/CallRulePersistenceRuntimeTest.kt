package com.lastcoffee.top.oppo_call_compose.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import com.lastcoffee.top.oppo_call_compose.model.CallStatusDisplayMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an in-memory database; the phone's saved rules and call history are untouched. */
@RunWith(AndroidJUnit4::class)
class CallRulePersistenceRuntimeTest {
    @Test fun editedDurationIsSharedByBothCallLayouts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        try {
            val store = ManualCallStore(context, database)
            val initial = store.configuration()
            store.saveCallStatuses(initial.statuses.map { status ->
                when (status.id) {
                    "waiting" -> status.copy(name = "共同等待", durationSeconds = 7)
                    "active" -> status.copy(displayText = "客服已接通", displayMode = CallStatusDisplayMode.TEXT_AND_TIMER)
                    "remote_hangup" -> status.copy(displayText = "服务结束", displayMode = CallStatusDisplayMode.TEXT_ONLY)
                    else -> status
                }
            })

            val first = store.prepareCall("13800138000", CallScreenVariant.WITHOUT_AVATAR)
            val second = store.prepareCall("10086", CallScreenVariant.WITH_AVATAR)
            assertEquals(CallScreenVariant.WITHOUT_AVATAR, first.screenVariant)
            assertEquals(CallScreenVariant.WITH_AVATAR, second.screenVariant)
            assertEquals(first.configuration, second.configuration)
            assertEquals(7, first.configuration.statuses.first { it.id == "waiting" }.durationSeconds)
            assertEquals("共同等待", second.configuration.statuses.first { it.id == "waiting" }.name)
            val active = second.configuration.statuses.first { it.id == "active" }
            assertEquals("客服已接通", active.displayText)
            assertEquals(CallStatusDisplayMode.TEXT_AND_TIMER, active.displayMode)
            val ended = second.configuration.statuses.first { it.id == "remote_hangup" }
            assertEquals("服务结束", ended.displayText)
            assertEquals(CallStatusDisplayMode.TEXT_ONLY, ended.displayMode)
        } finally {
            database.close()
        }
    }
}
