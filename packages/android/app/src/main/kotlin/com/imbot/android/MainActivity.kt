package com.imbot.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.imbot.android.ui.MainScreen
import com.imbot.android.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val deepLink = intent?.data
        if (deepLink?.scheme == "imbot") {
            when (deepLink.host) {
                "session" -> deepLink.lastPathSegment?.let(viewModel::openSessionFromNotification)
                "home" -> viewModel.openHomeFromNotification()
            }
            return
        }

        when (intent?.getStringExtra("action")) {
            "open_session" -> intent.getStringExtra("session_id")?.let(viewModel::openSessionFromNotification)
            "open_home" -> viewModel.openHomeFromNotification()
        }
    }
}
