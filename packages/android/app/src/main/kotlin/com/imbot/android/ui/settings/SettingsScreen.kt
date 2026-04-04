@file:Suppress("FunctionName")

package com.imbot.android.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
                connected = uiState.connectionState is ConnectionState.Connected,
                themeMode = uiState.themeMode,
                versionName = BuildConfig.VERSION_NAME,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
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
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "MacBook",
                        trailing = {
                            StatusPill(
                                text = statusLabel(uiState.macbookStatus),
                                online = uiState.macbookStatus == "online",
                            )
                        },
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "OpenClaw",
                        trailing = {
                            StatusPill(
                                text = statusLabel(uiState.openClawStatus),
                                online = uiState.openClawStatus == "online",
                            )
                        },
                        showDivider = false,
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
                        showDivider = true,
                    )
                    SettingsRow(
                        title = "会话保留天数",
                        value = "30 天",
                        showDivider = false,
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .appleChrome(
                        shape = componentShapes.card,
                        isDarkTheme = isDarkTheme,
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        shadowTokens = shadowTokens,
                    ),
            shape = componentShapes.card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
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
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        )
    }
}

@Composable
private fun SettingsTopBar(
    connected: Boolean,
    themeMode: String,
    versionName: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SYSTEM PREFERENCES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSummaryPill(
                label = if (connected) "Relay online" else "Relay offline",
                emphasized = connected,
            )
            SettingsSummaryPill(label = themeModeLabel(themeMode))
            SettingsSummaryPill(label = "v$versionName")
        }
    }
}

@Composable
private fun SettingsSummaryPill(
    label: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (emphasized) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    },
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    online: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (online) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun themeModeLabel(themeMode: String): String =
    when (themeMode) {
        SettingsRepository.THEME_MODE_LIGHT -> "Light mode"
        SettingsRepository.THEME_MODE_DARK -> "Dark mode"
        else -> "System theme"
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
