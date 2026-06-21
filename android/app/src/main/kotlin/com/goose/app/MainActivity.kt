package com.goose.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.goose.app.ui.AppShell
import com.goose.app.ui.theme.GooseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GooseTheme {
                AppShell()
            }
        }
    }
}
