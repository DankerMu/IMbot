@file:Suppress("FunctionName")

package com.imbot.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.R
import com.imbot.android.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
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
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.main_title))
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
                    text = stringResource(R.string.prototype_inputs_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${stringResource(R.string.provider_label)}: ${stringResource(R.string.provider_claude)}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = hostId,
                    onValueChange = viewModel::onHostIdChanged,
                    label = {
                        Text(text = stringResource(R.string.host_id_label))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = cwd,
                    onValueChange = viewModel::onCwdChanged,
                    label = {
                        Text(text = stringResource(R.string.cwd_label))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = viewModel::onPromptChanged,
                    label = {
                        Text(text = stringResource(R.string.prompt_label))
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
                        text = stringResource(R.string.current_session, sessionId.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = stringResource(R.string.event_stream_title),
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
