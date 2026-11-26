package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallStatusDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallRulesScreenRuntimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusEditorShowsTheBuiltInPromptForABlankOverride() {
        val configuration = CallFlowConfiguration().let { defaults ->
            defaults.copy(statuses = defaults.statuses.filter { it.id == "powered_off" })
        }
        compose.setContent {
            CallRulesScreen(configuration, PhoneExtras(), emptyList(), onAddMedia = {}, onBack = {}, onSave = {})
        }

        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("留空显示「您拨打的号码已关机」").assertIsDisplayed()
    }

    @Test fun statusEditorSavesIndependentScreenTextAndDisplayMode() {
        val defaults = CallFlowConfiguration()
        val active = defaults.statuses.first { it.id == "active" }
        var configuration by mutableStateOf(defaults.copy(statuses = listOf(active)))
        compose.setContent {
            CallRulesScreen(configuration, PhoneExtras(), emptyList(), onAddMedia = {}, onBack = {},
                onSave = { configuration = configuration.copy(statuses = it) })
        }
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("默认显示").performClick()
        compose.onNodeWithText("文字和计时").performClick()
        compose.onNodeWithText("通话界面文字").performTextInput("服务已接通")
        compose.onNodeWithText("保存").performClick()

        compose.runOnIdle {
            val saved = configuration.statuses.single()
            assertEquals("通话中", saved.name)
            assertEquals("服务已接通", saved.displayText)
            assertEquals(CallStatusDisplayMode.TEXT_AND_TIMER, saved.displayMode)
        }
    }

    @Test fun savedRulesReplaceTheInitialDefaultsWhenTheyLoad() {
        var configuration by mutableStateOf(CallFlowConfiguration())
        compose.setContent {
            CallRulesScreen(configuration, PhoneExtras(), emptyList(), onAddMedia = {}, onBack = {}, onSave = {})
        }

        compose.runOnUiThread {
            configuration = configuration.copy(statuses = configuration.statuses.map { status ->
                if (status.id == "waiting") status.copy(name = "已保存的等待", durationSeconds = 7) else status
            })
        }

        compose.onNodeWithText("已保存的等待").assertIsDisplayed()
        compose.onNodeWithText("等待接听 · 7 秒").assertIsDisplayed()
    }
}
