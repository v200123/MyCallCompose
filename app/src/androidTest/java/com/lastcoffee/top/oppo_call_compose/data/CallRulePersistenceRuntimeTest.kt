package com.lastcoffee.top.oppo_call_compose.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an in-memory database; the phone's saved rules and call history are untouched. */
@RunWith(AndroidJUnit4::class)
class CallRulePersistenceRuntimeTest {
    @Test fun editedDurationsAreSavedAndUsedForBothCallVariants() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        try {
            val store = ManualCallStore(context, database)
            val initial = store.configuration()
            val withoutAvatar = initial.copy(statuses = initial.statuses.map { status ->
                if (status.id == "waiting") status.copy(name = "无头像等待", durationSeconds = 7) else status
            })
            store.saveVariantConfiguration(CallScreenVariant.WITHOUT_AVATAR, withoutAvatar)
            val withAvatar = initial.copy(statuses = initial.avatarStatuses.map { status ->
                if (status.id == "waiting") status.copy(name = "有头像等待", durationSeconds = 11) else status
            })
            store.saveVariantConfiguration(CallScreenVariant.WITH_AVATAR, withAvatar)

            val first = store.prepareCall("13800138000", CallScreenVariant.WITHOUT_AVATAR)
            val second = store.prepareCall("10086", CallScreenVariant.WITH_AVATAR)
            assertEquals(7, first.configuration.statuses.first { it.id == "waiting" }.durationSeconds)
            assertEquals("无头像等待", first.configuration.statuses.first { it.id == "waiting" }.name)
            assertEquals(11, second.configuration.statuses.first { it.id == "waiting" }.durationSeconds)
            assertEquals("有头像等待", second.configuration.statuses.first { it.id == "waiting" }.name)
        } finally {
            database.close()
        }
    }
}
