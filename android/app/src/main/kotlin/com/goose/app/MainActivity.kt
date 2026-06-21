package com.goose.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goose.app.ble.BleConnectionState
import com.goose.app.ble.WhoopBleClient
import com.goose.app.ui.AppShell
import com.goose.app.ui.theme.GooseTheme

class MainActivity : ComponentActivity() {

    private val bleClient by lazy { WhoopBleClient(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GooseTheme {
                val connectionState: BleConnectionState by bleClient.connectionState
                    .collectAsStateWithLifecycle()

                AppShell(connectionState = connectionState)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleClient.disconnect()
    }
}
