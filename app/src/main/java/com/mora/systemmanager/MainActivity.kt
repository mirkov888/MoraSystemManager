package com.mora.systemmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mora.systemmanager.connector.MoraPolicyBridge
import com.mora.systemmanager.ui.MoraApp
import com.mora.systemmanager.ui.theme.MoraTheme

class MainActivity : ComponentActivity() {
    private lateinit var policyBridge: MoraPolicyBridge
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policyBridge = MoraPolicyBridge(applicationContext)
        setContent {
            MoraTheme {
                MoraApp(policyBridge)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        policyBridge.connect()
    }

    override fun onStop() {
        policyBridge.close()
        super.onStop()
    }
}
