package com.lastcoffee.top.oppo_call_compose.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NumberMediaBindingRuntimeTest {
    @Test fun dialedNumbersKeepTheirModeAndMediaAcrossDefaultChangesAndReloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        val directory = File(context.cacheDir, "binding-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = ManualCallStore(context, database)
            for (mode in listOf(CallAudioMode.RINGBACK, CallAudioMode.MUSIC)) {
                repeat(3) { index ->
                    val file = File(directory, "${mode.name}-$index").apply { writeText("test media") }
                    store.addMedia(CallMediaEntry(
                        file.name, mode, file.name, file.absolutePath,
                        if (mode == CallAudioMode.RINGBACK) "video/mp4" else "audio/mpeg",
                    ))
                }
            }
            store.setSelectedAudioMode(CallAudioMode.RINGBACK)
            val video = store.prepareCall("13700000001")
            store.setSelectedAudioMode(CallAudioMode.MUSIC)
            val music = store.prepareCall("13700000002")
            store.setSelectedAudioMode(CallAudioMode.DEFAULT_TONE)
            val tone = store.prepareCall("13700000003")
            val reloaded = ManualCallStore(context, database)
            repeat(5) {
                val repeatedVideo = reloaded.prepareCall("137 0000 0001")
                val repeatedMusic = reloaded.prepareCall("13700000002")
                assertEquals(CallAudioMode.RINGBACK, repeatedVideo.mode)
                assertEquals(video.waitingMediaId, repeatedVideo.waitingMediaId)
                assertEquals(CallAudioMode.MUSIC, repeatedMusic.mode)
                assertEquals(music.waitingMediaId, repeatedMusic.waitingMediaId)
                assertEquals(CallAudioMode.DEFAULT_TONE, reloaded.prepareCall("13700000003").mode)
                assertEquals("builtin_waiting", tone.waitingMediaId)
            }
            val simultaneous = coroutineScope {
                (1..5).map { async { reloaded.prepareCall("13700000002") } }.awaitAll()
            }
            assertTrue(simultaneous.all { it.waitingMediaId == music.waitingMediaId })

            // Editing other number settings submits the same mode.
            reloaded.updateNumberMode("13700000002", CallAudioMode.MUSIC)
            assertEquals(music.waitingMediaId, reloaded.prepareCall("13700000002").waitingMediaId)
            reloaded.deleteMedia(requireNotNull(music.waitingMediaId))
            val replacement = reloaded.prepareCall("13700000002")
            assertNotEquals(music.waitingMediaId, replacement.waitingMediaId)
            assertEquals(replacement.waitingMediaId, reloaded.prepareCall("13700000002").waitingMediaId)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test fun explicitRingbackBindingOverridesAutomaticSelectionAndRepairsOldNumberKeys() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ManualCallDatabase::class.java).build()
        val media = File(context.cacheDir, "bound-video-${System.nanoTime()}").apply { writeText("test media") }
        try {
            val dao = database.manualCallDao()
            val store = ManualCallStore(context, database)
            dao.saveNumberPool(CallNumberPoolEntity("+13700000004", CallAudioMode.DEFAULT_TONE.name))
            store.addMedia(CallMediaEntry("explicit-video", CallAudioMode.RINGBACK, "指定彩铃", media.absolutePath, "video/mp4"))
            val extras = PhoneExtras(ringbackBindings = mapOf("13700000004" to "explicit-video"))
            assertEquals("explicit-video", store.prepareCall("13700000004", extras = extras).waitingMediaId)
            assertEquals(CallAudioMode.RINGBACK, store.prepareCall("13700000004", extras = extras).mode)
            assertEquals(listOf("13700000004"), dao.getAllNumberPools().map { it.number })
            val blocked = extras.copy(noRingbackNumbers = setOf("13700000004"))
            assertEquals("builtin_waiting", store.prepareCall("13700000004", extras = blocked).waitingMediaId)
            assertEquals("explicit-video", store.prepareCall("13700000004", extras = extras).waitingMediaId)
        } finally {
            database.close()
            media.delete()
        }
    }
}
