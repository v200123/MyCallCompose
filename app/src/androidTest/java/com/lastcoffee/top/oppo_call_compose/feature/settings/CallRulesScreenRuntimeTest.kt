package com.lastcoffee.top.oppo_call_compose.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtras
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallRulesScreenRuntimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedRulesReplaceTheInitialDefaultsWhenTheyLoad() {
        var configuration by mutableStateOf(CallFlowConfiguration())
        compose.setContent {
            CallRulesScreen(configuration, PhoneExtras(), emptyList(), onAddMedia = {}, onBack = {}, onSave = { _, _ -> })
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
