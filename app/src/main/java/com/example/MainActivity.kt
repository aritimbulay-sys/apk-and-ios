package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.notifications.HotsMonNotificationHelper
import com.example.notifications.NotificationChannels
import com.example.ui.HotsMonApp
import com.example.ui.HotsMonViewModel
import com.example.ui.theme.HotsMonTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HotsMonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensureCreated(this)

        handleIntent(intent)

        setContent {
            HotsMonTheme {
                HotsMonApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val targetUrl = intent?.getStringExtra(HotsMonNotificationHelper.EXTRA_TARGET_URL)
        if (!targetUrl.isNullOrBlank()) {
            viewModel.handleIncomingDeepLink(targetUrl)
        }
    }
}
