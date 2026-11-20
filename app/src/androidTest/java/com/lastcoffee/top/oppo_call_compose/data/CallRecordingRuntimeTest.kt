package com.lastcoffee.top.oppo_call_compose.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallRecordingRuntimeTest {
    @Test fun importsAndReplacesARecordingForOnlyTheSelectedCall() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        val store = ManualCallStore(context, database)
        val source = File.createTempFile("call-recording-test", ".mp3", context.cacheDir)
        val invalid = File.createTempFile("call-recording-invalid", ".mp3", context.cacheDir)
        val dao = database.manualCallDao()
        val first = ManualCallEntity("first", "5550100", "OUTGOING", 1, 10L, true,
            null, null, 0L, 0L, 0, null, 0L)
        val second = first.copy(id = "second")
        try {
            context.resources.openRawResource(R.raw.audio_call).use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            dao.insert(first)
            dao.insert(second)
            store.saveRecording(second.id, null, 7_000)
            assertNull(dao.getCall(second.id)!!.recordingPath)
            assertEquals(7_000L, dao.getCall(second.id)!!.recordingDurationMillis)

            store.saveRecording(first.id, Uri.fromFile(source), null)
            val imported = dao.getCall(first.id)!!
            val originalFile = store.recordingFile(checkNotNull(imported.recordingPath))
            assertTrue(originalFile.readBytes().contentEquals(source.readBytes()))
            assertTrue(imported.recordingDurationMillis in 2_000L..3_000L)

            assertTrue(runCatching { store.saveRecording(first.id, Uri.fromFile(invalid), null) }.isFailure)
            assertEquals(imported, dao.getCall(first.id))
            assertTrue(originalFile.isFile)

            store.saveRecording(first.id, Uri.fromFile(source), 5_000)
            val replacement = dao.getCall(first.id)!!
            assertNotEquals(imported.recordingPath, replacement.recordingPath)
            assertFalse(originalFile.exists())
            assertEquals(5_000L, replacement.recordingDurationMillis)
            val replacementFile = store.recordingFile(checkNotNull(replacement.recordingPath))
            assertTrue(replacementFile.isFile)
            store.delete(store.calls.first().first { it.id == first.id }.toCallRecord())
            assertFalse(replacementFile.exists())
            assertEquals(7_000L, dao.getCall(second.id)!!.recordingDurationMillis)
        } finally {
            dao.getCall(first.id)?.recordingPath?.let { store.recordingFile(it).delete() }
            source.delete()
            invalid.delete()
            database.close()
        }
    }

    @Test fun bundledWaitingAndUnreachableAudioStayAvailableAcrossConfigTransfer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        try {
            val store = ManualCallStore(context, database)
            val exported = store.exportTransferState()
            assertTrue(exported.mediaEntries.isEmpty())
            store.replaceTransferState(exported)
            val assets = store.mediaEntries.first().associateBy { it.id }
            assertEquals(CallAudioMode.DEFAULT_TONE, assets.getValue("builtin_waiting").mode)
            assertTrue(File(assets.getValue("builtin_waiting").filePath).isFile)
            assertTrue(File(assets.getValue("builtin_unreachable").filePath).isFile)
            assertEquals("builtin_unreachable", store.configuration().statuses.first { it.id == "unreachable" }.mediaEntryId)
        } finally {
            database.close()
        }
    }
}
