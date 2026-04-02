@file:Suppress("FunctionName")

package com.imbot.android.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.data.repository.HostWithRoots
import com.imbot.android.network.RelayWorkspaceRoot
import com.imbot.android.ui.components.EmptyState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.ShimmerSkeleton
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalProviderColors
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import com.imbot.android.ui.theme.providerColorFor
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    errorState: ErrorState,
    onOpenRoot: (RelayWorkspaceRoot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addRootState by viewModel.addRootState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
        )

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is WorkspaceEvent.ShowMessage) {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Short,
                )
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                title = {
                    Text("目录管理")
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddRootSheet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier.appleChrome(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        isDarkTheme = isDarkTheme,
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                        shadowTokens = shadowTokens,
                    ),
            ) {
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            ErrorBannerHost(
                errorState = errorState,
                scope = ErrorScope.WORKSPACE,
                modifier = Modifier.padding(horizontal = 16.dp),
                hostId = workspaceBannerHostId(uiState.hosts),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState),
            ) {
                when {
                    uiState.isLoading -> {
                        WorkspaceLoadingState(modifier = Modifier.fillMaxSize())
                    }

                    uiState.error != null && uiState.hosts.isEmpty() -> {
                        WorkspaceEmptyState(
                            title = "加载失败",
                            description = uiState.error!!,
                            buttonLabel = "重试",
                            onAction = viewModel::refresh,
                        )
                    }

                    uiState.hosts.isEmpty() -> {
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
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(
                                items = uiState.hosts,
                                key = { hostWithRoots -> hostWithRoots.host.id },
                            ) { hostWithRoots ->
                                HostSection(
                                    hostWithRoots = hostWithRoots,
                                    onOpenRoot = onOpenRoot,
                                    onRemoveRoot = { root ->
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

                PullRefreshIndicator(
                    refreshing = uiState.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun HostSection(
    hostWithRoots: HostWithRoots,
    onOpenRoot: (RelayWorkspaceRoot) -> Unit,
    onRemoveRoot: (RelayWorkspaceRoot) -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HostHeader(
            name = hostWithRoots.host.name,
            status = hostWithRoots.host.status,
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .appleChrome(
                        shape = componentShapes.card,
                        isDarkTheme = isDarkTheme,
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shadowTokens = shadowTokens,
                    ),
            shape = componentShapes.card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                if (hostWithRoots.roots.isEmpty()) {
                    EmptyHostRootsPlaceholder()
                } else {
                    hostWithRoots.roots.forEachIndexed { index, root ->
                        WorkspaceRootRow(
                            root = root,
                            onClick = {
                                onOpenRoot(root)
                            },
                            onRemove = {
                                onRemoveRoot(root)
                            },
                        )
                        if (index < hostWithRoots.roots.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHostRootsPlaceholder() {
    Text(
        text = "暂无目录",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                                MaterialTheme.colorScheme.secondary
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
    val providerColor = providerColorFor(root.provider, LocalProviderColors.current)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(
                        color = providerColor.copy(alpha = 0.18f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = root.provider.uppercase().take(2),
                color = providerColor,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    EmptyState(
        illustration = { WorkspaceEmptyIllustration() },
        title = title,
        subtitle = description,
        ctaText = buttonLabel,
        onCta = onAction,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun WorkspaceLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerSkeleton(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.35f)
                            .size(width = 120.dp, height = 20.dp),
                )
                repeat(2) {
                    ShimmerSkeleton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEmptyIllustration() {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
    )
}

private fun workspaceBannerHostId(hosts: List<HostWithRoots>): String? =
    hosts
        .firstOrNull { hostWithRoots ->
            hostWithRoots.host.type == "macbook" || hostWithRoots.host.type == "companion"
        }?.host?.id
