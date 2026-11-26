package com.lastcoffee.top.oppo_call_compose.feature.call

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallMediaEntry
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.withProgrammedCallFlow
import com.lastcoffee.top.oppo_call_compose.model.CallButtonId
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusDisplayMode
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.ManualCall
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real screen and timers, without a phone call or changes to saved rules. */
@RunWith(AndroidJUnit4::class)
class InCallRuleRuntimeTest {
    @get:Rule val compose = createComposeRule()
    private val finished = AtomicReference<ManualCall?>()
    private val completions = AtomicInteger()

    private fun launch(vararg statuses: CallStatusRule) {
        val configuration = CallFlowConfiguration(initialStatusId = statuses.first().id, statuses = statuses.toList())
        compose.setContent {
            OPPO_Call_ComposeTheme {
                InCallScreen(
                    number = "13800138000", mode = CallAudioMode.DEFAULT_TONE,
                    initialStatusId = configuration.initialStatusId, configuration = configuration,
                    mediaEntries = emptyList(), resolvedLocation = "北京 移动",
                    onCallFinished = { finished.set(it); completions.incrementAndGet() },
                )
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "rule-verification/$name.png")
        file.parentFile!!.mkdirs()
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun customStatusTextAndTimerAreRenderedAndCanChangeAtHangup() {
        launch(
            CallStatusRule("active", "通话中", CallStage.ACTIVE,
                displayText = "客服已接通", displayMode = CallStatusDisplayMode.TEXT_AND_TIMER),
            CallStatusRule("disconnecting", "正在挂断", CallStage.DISCONNECTING, 3, "end",
                displayText = "服务结束", displayMode = CallStatusDisplayMode.TEXT_ONLY),
            CallStatusRule("end", "已挂断", CallStage.DISCONNECTED),
        )

        waitForText("客服已接通")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("00:01")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("挂断").performClick()
        waitForText("服务结束")
        compose.onNode(hasText("00:", substring = true)).assertDoesNotExist()
        compose.waitUntil(8_000) { finished.get() != null }
        assertTrue(finished.get()!!.durationSeconds >= 1)
    }

    @Test fun poweredOffStatusShowsTheFullDefaultPrompt() {
        val rule = defaultCallStatuses().first { it.id == "powered_off" }
        launch(rule.copy(mediaEntryId = null))
        waitForText("您拨打的号码已关机")
    }

    @Test fun unansweredMusicPlaysBeforeTheOutcomeAndNeverConnects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val song = File(context.cacheDir, "unanswered-preview-song.mp3")
        val prompt = File(context.cacheDir, "unanswered-preview-prompt.mp3")
        context.resources.openRawResource(R.raw.audio_call).use { input -> song.outputStream().use(input::copyTo) }
        context.resources.openRawResource(R.raw.telephone_hang_up_call).use { input -> prompt.outputStream().use(input::copyTo) }
        val configuration = CallFlowConfiguration().withProgrammedCallFlow().let { defaults ->
            defaults.copy(statuses = defaults.statuses.map { status ->
                when (status.id) {
                    "dialing" -> status.copy(durationSeconds = 1)
                    "waiting" -> status.copy(displayText = "等待响铃")
                    "no_answer" -> status.copy(mediaEntryId = "test-prompt", audioPlayCount = 1)
                    else -> status
                }
            })
        }
        compose.setContent {
            OPPO_Call_ComposeTheme {
                InCallScreen(
                    number = "13700000005", mode = CallAudioMode.MUSIC,
                    initialStatusId = "dialing", configuration = configuration, waitingMediaId = "test-song",
                    mediaEntries = listOf(
                        CallMediaEntry("test-song", CallAudioMode.MUSIC, "测试音乐", song.absolutePath, "audio/mpeg"),
                        CallMediaEntry("test-prompt", CallAudioMode.STATUS_SOUND, "测试提示", prompt.absolutePath, "audio/mpeg"),
                    ),
                    extras = PhoneExtras(promptNumbers = mapOf("13700000005" to CallPromptType.NO_ANSWER)),
                    onCallFinished = { finished.set(it); completions.incrementAndGet() },
                )
            }
        }
        waitForText("等待响铃")
        waitForText("等待接听")
        compose.waitUntil(8_000) { finished.get() != null }
        assertFalse(finished.get()!!.wasConnected)
        assertEquals(0, finished.get()!!.durationSeconds)
        assertEquals(1, completions.get())
    }

    @Test fun voicemailGreetingConnectsAndContinuesIntoTheMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val greeting = File(context.cacheDir, "voicemail-preview-greeting.mp3")
        context.resources.openRawResource(R.raw.audio_call).use { input -> greeting.outputStream().use(input::copyTo) }
        val configuration = CallFlowConfiguration().withProgrammedCallFlow().let { defaults ->
            defaults.copy(statuses = defaults.statuses.map { status ->
                when (status.id) {
                    "dialing" -> status.copy(durationSeconds = 1)
                    "voicemail" -> status.copy(mediaEntryId = "test-greeting")
                    else -> status
                }
            })
        }
        compose.setContent {
            OPPO_Call_ComposeTheme {
                InCallScreen(
                    number = "13700000006", mode = CallAudioMode.DEFAULT_TONE,
                    initialStatusId = "dialing", configuration = configuration,
                    mediaEntries = listOf(CallMediaEntry("test-greeting", CallAudioMode.STATUS_SOUND, "留言引导", greeting.absolutePath, "audio/mpeg")),
                    extras = PhoneExtras(promptNumbers = mapOf("13700000006" to CallPromptType.VOICEMAIL)),
                    onCallFinished = { finished.set(it); completions.incrementAndGet() },
                )
            }
        }
        waitForText("语音留言")
        compose.onNodeWithText("等待接听").assertDoesNotExist()
        waitForText("留言中")
        screenshot("voicemail-connected")
        compose.onNodeWithContentDescription("底部挂断区域").performTouchInput { click() }
        compose.waitUntil(5_000) { finished.get() != null }
        assertTrue(finished.get()!!.wasConnected)
        assertTrue(finished.get()!!.durationSeconds >= 2)
        assertEquals(1, completions.get())
    }

    @Test fun automaticChainKeepsRunningWhileOrdinaryControlsAreUsed() {
        launch(
            CallStatusRule("dial", "规则自动呼叫", CallStage.DIALING, 1, "wait"),
            CallStatusRule("wait", "规则自动等待", CallStage.WAITING, 2, "talk"),
            CallStatusRule("talk", "规则自动通话", CallStage.ACTIVE, 10, "hold"),
            CallStatusRule("hold", "规则自动保持", CallStage.HOLD, 2, "ending"),
            CallStatusRule("ending", "规则自动挂断", CallStage.DISCONNECTING, 1, "end"),
            CallStatusRule("end", "规则自动结束", CallStage.DISCONNECTED, 1),
        )
        compose.onNodeWithText("接通").assertDoesNotExist()
        waitForText("规则自动通话")
        compose.onNodeWithContentDescription("扬声器").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("静音").performClick().assertIsSelected()
        screenshot("automatic-active")
        compose.onNodeWithContentDescription("拨号盘").performClick()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("#").performClick()
        compose.onNodeWithText("12#").assertIsDisplayed()
        compose.onNodeWithText("规则自动通话").assertIsDisplayed()
        screenshot("automatic-keypad")
        compose.onNodeWithContentDescription("拨号盘").performClick()
        waitForText("规则自动保持")
        screenshot("automatic-hold")
        compose.waitUntil(10_000) { finished.get() != null }
        assertTrue(finished.get()!!.wasConnected)
        assertTrue(finished.get()!!.durationSeconds >= 12)
        assertEquals(1, completions.get())
    }

    @Test fun tappingBottomSpaceFinishesTheConnectedCall() {
        launch(
            CallStatusRule("talk", "通话中", CallStage.ACTIVE),
            CallStatusRule("ending", "正在挂断", CallStage.DISCONNECTING, 0, "end"),
            CallStatusRule("end", "已挂断", CallStage.DISCONNECTED),
        )

        compose.onNodeWithContentDescription("底部挂断区域").performTouchInput { click() }
        compose.waitUntil(5_000) { finished.get() != null }
        assertTrue(finished.get()!!.wasConnected)
        assertEquals(1, completions.get())
    }

    @Test fun manualHangupCancelsPendingAutomaticConnection() {
        launch(
            CallStatusRule("wait", "中断前等待", CallStage.WAITING, 6, "talk"),
            CallStatusRule("talk", "不应出现的接通", CallStage.ACTIVE, 20, "end"),
            CallStatusRule("end", "中断后结束", CallStage.DISCONNECTED, 1),
        )
        compose.onNodeWithContentDescription("挂断").performClick()
        compose.waitUntil(8_000) { finished.get() != null }
        // Let the canceled connection deadline pass on the actual device clock.
        SystemClock.sleep(6_500)
        compose.mainClock.advanceTimeBy(7_000)
        compose.onNodeWithText("不应出现的接通").assertDoesNotExist()
        assertFalse(finished.get()!!.wasConnected)
        assertEquals(0, finished.get()!!.durationSeconds)
        assertEquals(1, completions.get())
        screenshot("manual-interruption")
    }

    @Test fun configuredButtonReplacesTheAutomaticBranch() {
        val waiting = CallStatusRule("wait", "分支前等待", CallStage.WAITING, 7, "talk")
        launch(
            waiting.copy(buttons = waiting.buttons.map {
                if (it.id == CallButtonId.SPEAKER) it.copy(targetStatusId = "branch") else it
            }),
            CallStatusRule("talk", "不应进入的原分支", CallStage.ACTIVE),
            CallStatusRule("branch", "操作切换的新分支", CallStage.OUTCOME, 1, "end"),
            CallStatusRule("end", "分支执行结束", CallStage.DISCONNECTED, 1),
        )
        compose.onNodeWithContentDescription("扬声器").performClick()
        waitForText("操作切换的新分支")
        compose.waitUntil(8_000) { finished.get() != null }
        SystemClock.sleep(7_000)
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("不应进入的原分支").assertDoesNotExist()
        assertFalse(finished.get()!!.wasConnected)
        assertEquals(1, completions.get())
    }

}
