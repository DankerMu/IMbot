@file:Suppress("FunctionName")

package com.imbot.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.ui.SettingsSection
import com.imbot.android.ui.StatusBar
import com.imbot.android.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenPrototype: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relayUrl by viewModel.relayUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("设置")
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBar(connectionState = connectionState)

            SettingsSection(
                relayUrl = relayUrl,
                token = token,
                onRelayUrlChange = viewModel::onRelayUrlChanged,
                onTokenChange = viewModel::onTokenChanged,
                onSave = viewModel::saveSettings,
            )

            Button(
                onClick = onOpenPrototype,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开原型调试")
            }
        }
    }
}
