@file:Suppress("FunctionName")

package com.imbot.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.BuildConfig
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.ConnectionState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is SettingsEvent.ShowMessage) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (showEditDialog) {
        EditRelayDialog(
            initialUrl = uiState.relayUrl,
            onDismiss = {
                showEditDialog = false
            },
            onSave = { newUrl ->
                showEditDialog = false
                viewModel.updateRelayUrl(newUrl)
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearCache()
                    },
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                    },
                ) {
                    Text("取消")
                }
            },
            title = {
                Text("确认清除本地缓存？")
            },
            text = {
                Text("这会删除本地缓存的会话和事件，下次进入相关页面时会重新从 Relay 拉取。")
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("设置")
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "connection") {
                SettingsSection(title = "连接") {
                    SettingsRow(
                        title = "Relay URL",
                        value = uiState.relayUrl.ifBlank { "未配置" },
                        onClick = {
                            showEditDialog = true
                        },
                    )
                    SettingsRow(
                        title = "连接状态",
                        trailing = {
                            StatusPill(
                                text =
                                    if (uiState.connectionState is ConnectionState.Connected) {
                                        "已连接"
                                    } else {
                                        "未连接"
                                    },
                                online = uiState.connectionState is ConnectionState.Connected,
                            )
                        },
                    )
                    SettingsRow(
                        title = "MacBook",
                        trailing = {
                            StatusPill(
                                text = statusLabel(uiState.macbookStatus),
                                online = uiState.macbookStatus == "online",
                            )
                        },
                    )
                    SettingsRow(
                        title = "OpenClaw",
                        trailing = {
                            StatusPill(
                                text = statusLabel(uiState.openClawStatus),
                                online = uiState.openClawStatus == "online",
                            )
                        },
                    )
                }
            }

            item(key = "appearance") {
                SettingsSection(title = "外观") {
                    ThemeOptions(
                        selectedMode = uiState.themeMode,
                        onModeSelected = viewModel::setTheme,
                    )
                }
            }

            item(key = "data") {
                SettingsSection(title = "数据") {
                    SettingsRow(
                        title = "清除本地缓存",
                        value = "删除本地 sessions / events 缓存",
                        onClick = {
                            showClearDialog = true
                        },
                    )
                    SettingsRow(
                        title = "会话保留天数",
                        value = "30 天",
                    )
                }
            }

            item(key = "about") {
                SettingsSection(title = "关于") {
                    SettingsRow(
                        title = "版本",
                        value = BuildConfig.VERSION_NAME,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.large,
                    ),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) {
                    onClick?.invoke()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            value?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun StatusPill(
    text: String,
    online: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        color =
                            if (online) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        shape = CircleShape,
                    ),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeOptions(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
) {
    val options =
        listOf(
            SettingsRepository.THEME_MODE_SYSTEM to "跟随系统",
            SettingsRepository.THEME_MODE_LIGHT to "浅色",
            SettingsRepository.THEME_MODE_DARK to "深色",
        )

    Column {
        options.forEach { (mode, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onModeSelected(mode)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = {
                        onModeSelected(mode)
                    },
                )
                Text(label)
            }
        }
    }
}

private fun statusLabel(status: String): String =
    if (status == "online") {
        "在线"
    } else {
        "离线"
    }
