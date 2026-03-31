package com.imbot.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.SettingsRepository
import com.imbot.android.service.SessionService
import com.imbot.android.ui.home.HomeViewModel
import com.imbot.android.ui.navigation.AppNavigation
import com.imbot.android.ui.navigation.resolveStartDestination
import com.imbot.android.ui.theme.IMbotTheme
import com.imbot.android.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialSettings = settingsRepository.load()
        val startDestination = resolveStartDestination(initialSettings)
        setContent {
            val themeMode by
                settingsRepository.observeThemeMode().collectAsStateWithLifecycle(
                    initialValue = settingsRepository.loadThemeMode(),
                )

            IMbotTheme(themeMode = themeMode) {
                Surface {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        mainViewModel = viewModel,
                        startDestination = startDestination,
                    )
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

    override fun onStart() {
        super.onStart()
        if (settingsRepository.load().isConfigured()) {
            SessionService.start(this)
        }
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
