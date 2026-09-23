package com.lastcoffee.top.oppo_call_compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
                OppoPhoneApp(
                    onDial = { number ->
                        if (number.isBlank()) {
                            Toast.makeText(this, "请先输入号码", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        }
                    }
                )
            }
        }
    }
}
