package com.lastcoffee.top.oppo_call_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.appupdater.AppUpdater
import com.example.appupdater.UpdateConfig
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

class MainActivity : ComponentActivity() {
    private lateinit var appUpdater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdater = AppUpdater.register(
            activity = this,
            config = UpdateConfig(baseUrl = BuildConfig.UPDATE_SERVICE_BASE_URL),
        )
        enableEdgeToEdge()
        setContent {
            OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
                OppoPhoneApp(onCheckForUpdate = appUpdater::checkAndShowUserInitiated)
            }
        }
        appUpdater.checkAndShow()
    }
}
