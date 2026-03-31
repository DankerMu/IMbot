@file:Suppress("FunctionName")

package com.imbot.android.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.network.RelayWorkspaceRoot
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onOpenRoot: (RelayWorkspaceRoot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addRootState by viewModel.addRootState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
        )

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is WorkspaceEvent.ShowMessage) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (uiState.pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveRootDialog,
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoveRoot) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveRootDialog) {
                    Text("取消")
                }
            },
            title = {
                Text("确认移除此根目录？")
            },
            text = {
                Text("已有会话不受影响。")
            },
        )
    }

    if (addRootState.isVisible) {
        AddRootBottomSheet(
            state = addRootState,
            onDismiss = viewModel::dismissAddRootSheet,
            onProviderSelected = viewModel::selectProvider,
            onBrowseDirectory = viewModel::browseAddRootDirectory,
            onBrowseUp = viewModel::browseAddRootUp,
            onLabelChanged = viewModel::updateAddRootLabel,
            onSubmit = viewModel::submitAddRoot,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("目录管理")
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddRootSheet) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加根目录",
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pullRefresh(pullRefreshState),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在加载目录…")
                    }
                }

                uiState.error != null && uiState.hosts.isEmpty() -> {
                    WorkspaceEmptyState(
                        title = "加载失败",
                        description = uiState.error!!,
                        buttonLabel = "重试",
                        onAction = viewModel::refresh,
                    )
                }

                uiState.hosts.all { host -> host.roots.isEmpty() } -> {
                    WorkspaceEmptyState(
                        title = "暂无根目录",
                        description = "添加一个根目录后，就能直接按目录恢复会话。",
                        buttonLabel = "添加根目录",
                        onAction = viewModel::showAddRootSheet,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        uiState.hosts.forEach { hostWithRoots ->
                            item(key = "host-${hostWithRoots.host.id}") {
                                HostHeader(
                                    name = hostWithRoots.host.name,
                                    status = hostWithRoots.host.status,
                                )
                            }

                            items(
                                items = hostWithRoots.roots,
                                key = { root -> root.id },
                            ) { root ->
                                WorkspaceRootRow(
                                    root = root,
                                    onClick = {
                                        onOpenRoot(root)
                                    },
                                    onRemove = {
                                        viewModel.requestRemoveRoot(
                                            hostId = hostWithRoots.host.id,
                                            rootId = root.id,
                                            label = root.label ?: root.path.defaultRootLabel(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun HostHeader(
    name: String,
    status: String,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        color =
                            if (status == "online") {
                                Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (status == "online") "在线" else "离线",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkspaceRootRow(
    root: RelayWorkspaceRoot,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .background(
                        color = rootProviderColor(root.provider).copy(alpha = 0.18f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = root.provider.uppercase().take(2),
                color = rootProviderColor(root.provider),
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = root.label ?: root.path.defaultRootLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = root.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除根目录",
            )
        }
    }
}

@Composable
private fun WorkspaceEmptyState(
    title: String,
    description: String,
    buttonLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAction) {
                Text(buttonLabel)
            }
        }
    }
}

private fun rootProviderColor(provider: String): Color =
    when (provider) {
        "claude" -> Color(0xFFFFB74D)
        "book" -> Color(0xFF8E6AD9)
        "openclaw" -> Color(0xFFE57373)
        else -> Color.Gray
    }
