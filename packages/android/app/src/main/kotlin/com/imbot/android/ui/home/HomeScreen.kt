@file:Suppress("FunctionName")

package com.imbot.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.components.EmptyState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.ShimmerSkeleton
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import com.imbot.android.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    errorState: ErrorState,
    onCreateSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBulkDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
        )

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    LaunchedEffect(uiState.isSelectionMode) {
        if (!uiState.isSelectionMode) {
            showBulkDeleteDialog = false
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showBulkDeleteDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                        viewModel.deleteSelectedSessions()
                    },
                    enabled = !uiState.isDeletingSelection,
                ) {
                    Text(if (uiState.isDeletingSelection) "删除中..." else "删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                    },
                ) {
                    Text("取消")
                }
            },
            title = {
                Text("删除 ${uiState.selectedSessionIds.size} 个会话？")
            },
            text = {
                Text("该操作会逐个调用现有删除接口。删除成功的会话将从列表中移除。")
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopAppBar(
                filter = uiState.filter,
                selectionCount = uiState.selectedSessionIds.size,
                totalCount = uiState.sessions.size,
                runningCount = uiState.runningSessionCount,
                isConnected = uiState.isConnected,
                allVisibleSelected = uiState.allVisibleSelected,
                isSelectionMode = uiState.isSelectionMode,
                isDeletingSelection = uiState.isDeletingSelection,
                onFilterSelected = viewModel::applyFilter,
                onToggleSelectAll = viewModel::toggleSelectAllVisibleSessions,
                onDeleteSelected = {
                    showBulkDeleteDialog = true
                },
                onClearSelection = viewModel::clearSelection,
            )
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = onCreateSession,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier.appleChrome(
                            shape = CircleShape,
                            isDarkTheme = isDarkTheme,
                            outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                            shadowTokens = shadowTokens,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "新建会话",
                    )
                }
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
                scope = ErrorScope.GLOBAL,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState),
            ) {
                when {
                    uiState.isLoading -> {
                        SessionListSkeleton(modifier = Modifier.fillMaxSize())
                    }

                    uiState.sessions.isEmpty() -> {
                        HomeEmptyState(
                            isConnected = uiState.isConnected,
                            onCreateSession = onCreateSession,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        SessionListContent(
                            state = uiState,
                            onDeleteSession = viewModel::deleteSession,
                            onOpenSession = onOpenSession,
                            onEnterSelectionMode = viewModel::enterSelectionMode,
                            onToggleSelection = viewModel::toggleSessionSelection,
                        )
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
private fun SessionListContent(
    state: HomeUiState,
    onDeleteSession: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    val runningSessions = state.sessions.filter { session -> isRunningStatus(session.status) }
    val otherSessions = state.sessions.filterNot { session -> isRunningStatus(session.status) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (runningSessions.isNotEmpty()) {
            item(key = "running-header") {
                SessionSectionHeader(
                    title = "Running Now",
                    meta = "${runningSessions.size} active",
                )
            }
        }

        items(
            items = runningSessions,
            key = { session -> session.id },
        ) { session ->
            SessionCard(
                session = session,
                onClick = {
                    if (state.isSelectionMode) {
                        onToggleSelection(session.id)
                    } else {
                        onOpenSession(session.id)
                    }
                },
                onLongPress = {
                    if (state.isSelectionMode) {
                        onToggleSelection(session.id)
                    } else {
                        onEnterSelectionMode(session.id)
                    }
                },
                onDelete = {
                    onDeleteSession(session.id)
                },
                selected = session.id in state.selectedSessionIds,
                selectionMode = state.isSelectionMode,
                allowDelete = !state.isSelectionMode,
            )
        }

        if (otherSessions.isNotEmpty()) {
            item(key = "recent-header") {
                SessionSectionHeader(
                    title = if (runningSessions.isNotEmpty()) "Recent" else "All Sessions",
                    meta = "${otherSessions.size} sessions",
                )
            }
        }

        items(
            items = otherSessions,
            key = { session -> session.id },
        ) { session ->
            SessionCard(
                session = session,
                onClick = {
                    if (state.isSelectionMode) {
                        onToggleSelection(session.id)
                    } else {
                        onOpenSession(session.id)
                    }
                },
                onLongPress = {
                    if (state.isSelectionMode) {
                        onToggleSelection(session.id)
                    } else {
                        onEnterSelectionMode(session.id)
                    }
                },
                onDelete = {
                    onDeleteSession(session.id)
                },
                selected = session.id in state.selectedSessionIds,
                selectionMode = state.isSelectionMode,
                allowDelete = !state.isSelectionMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    filter: String?,
    selectionCount: Int,
    totalCount: Int,
    runningCount: Int,
    isConnected: Boolean,
    allVisibleSelected: Boolean,
    isSelectionMode: Boolean,
    isDeletingSelection: Boolean,
    onFilterSelected: (String?) -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val spacing = MaterialTheme.spacing

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isSelectionMode) "BULK ACTIONS" else "WORKSPACE CONSOLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isSelectionMode) "已选 $selectionCount 项" else "Sessions",
                style = MaterialTheme.typography.headlineLarge,
            )
            if (isSelectionMode) {
                Text(
                    text = "批量删除会逐个调用当前会话删除接口。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isSelectionMode) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSummaryPill(
                    label = if (isConnected) "Relay Online" else "Relay Offline",
                    emphasized = isConnected,
                )
                HomeSummaryPill(
                    label = "$runningCount running",
                    emphasized = runningCount > 0,
                )
                HomeSummaryPill(label = "$totalCount total")
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSelectionMode) {
                SelectionActionChip(
                    text = if (allVisibleSelected) "取消全选" else "全选",
                    onClick = onToggleSelectAll,
                    enabled = !isDeletingSelection,
                )
                SelectionActionChip(
                    text = if (isDeletingSelection) "删除中..." else "删除",
                    onClick = onDeleteSelected,
                    enabled = !isDeletingSelection,
                    destructive = true,
                )
                SelectionActionChip(
                    text = "完成",
                    onClick = onClearSelection,
                    enabled = !isDeletingSelection,
                )
            } else {
                providerFilterOptions().forEach { option ->
                    ProviderFilterChip(
                        text = option.label,
                        selected = filter == option.value,
                        shape = componentShapes.button,
                        onClick = {
                            onFilterSelected(option.value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSummaryPill(
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
                1.dp,
                if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
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
private fun ProviderFilterChip(
    text: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Surface(
        shape = shape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                },
            ),
        modifier =
            Modifier.clickable {
                onClick()
            },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun SessionSectionHeader(
    title: String,
    meta: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    destructive: Boolean = false,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    Surface(
        shape = componentShapes.button,
        color =
            when {
                destructive && enabled -> MaterialTheme.colorScheme.errorContainer
                enabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        border =
            BorderStroke(
                1.dp,
                when {
                    destructive && enabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                    enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                },
            ),
        modifier =
            Modifier.clickable(enabled = enabled) {
                onClick()
            },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                when {
                    destructive && enabled -> MaterialTheme.colorScheme.onErrorContainer
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        )
    }
}

@Composable
private fun HomeEmptyState(
    isConnected: Boolean,
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = { SessionEmptyIllustration() },
        title = "暂无会话",
        subtitle =
            if (isConnected) {
                "通过下方入口创建新会话，或等待后台刷新缓存。"
            } else {
                "当前离线，可先查看本地缓存，连接恢复后会自动刷新。"
            },
        ctaText = "新建会话",
        onCta = onCreateSession,
        modifier = modifier,
    )
}

@Composable
private fun SessionListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp))
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp))
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp))
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionEmptyIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.extraLarge,
                ),
    )
}

private fun providerFilterOptions() =
    listOf(
        ProviderFilterOption(label = "全部", value = null),
        ProviderFilterOption(label = "Claude Code", value = "claude"),
        ProviderFilterOption(label = "book", value = "book"),
        ProviderFilterOption(label = "OpenClaw", value = "openclaw"),
    )

private data class ProviderFilterOption(
    val label: String,
    val value: String?,
)
