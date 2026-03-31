@file:Suppress("FunctionName")

package com.imbot.android.ui.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.ui.CreateSessionButton
import com.imbot.android.ui.EventList
import com.imbot.android.ui.SettingsSection
import com.imbot.android.ui.StatusBar
import com.imbot.android.viewmodel.MainViewModel
import com.imbot.android.viewmodel.PrototypeInputField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relayUrl by viewModel.relayUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val hostId by viewModel.hostId.collectAsStateWithLifecycle()
    val cwd by viewModel.cwd.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()
    val noticeIsError by viewModel.noticeIsError.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("原型调试")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSection(
                    relayUrl = relayUrl,
                    token = token,
                    onRelayUrlChange = viewModel::onRelayUrlChanged,
                    onTokenChange = viewModel::onTokenChanged,
                    onSave = viewModel::saveSettings,
                )

                StatusBar(connectionState = connectionState)

                Text(
                    text = "原型会话输入",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Provider: Claude Code",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = hostId,
                    onValueChange = { value ->
                        viewModel.onPrototypeInputChanged(PrototypeInputField.HostId, value)
                    },
                    label = {
                        Text("Host ID")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = cwd,
                    onValueChange = { value ->
                        viewModel.onPrototypeInputChanged(PrototypeInputField.Cwd, value)
                    },
                    label = {
                        Text("Working Directory")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { value ->
                        viewModel.onPrototypeInputChanged(PrototypeInputField.Prompt, value)
                    },
                    label = {
                        Text("Prompt")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )

                CreateSessionButton(
                    enabled = relayUrl.isNotBlank() && token.isNotBlank(),
                    isCreating = isCreating,
                    onClick = viewModel::createSession,
                )

                if (!noticeMessage.isNullOrBlank()) {
                    Text(
                        text = noticeMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (noticeIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                    )
                }

                if (!sessionId.isNullOrBlank()) {
                    Text(
                        text = "当前会话: ${sessionId.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = "事件流",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            EventList(
                events = events,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
