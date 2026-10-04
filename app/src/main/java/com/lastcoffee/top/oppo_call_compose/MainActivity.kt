package com.lastcoffee.top.oppo_call_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) {
                OppoPhoneApp()
            }
        }
    }
}
