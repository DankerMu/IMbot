@file:Suppress("FunctionName")

package com.imbot.android.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.components.EmptyState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.ShimmerSkeleton

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

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeTopAppBar(
                filter = uiState.filter,
                onFilterSelected = viewModel::applyFilter,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSession) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "新建会话",
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
) {
    val runningSessions = state.sessions.filter { session -> isRunningStatus(session.status) }
    val otherSessions = state.sessions.filterNot { session -> isRunningStatus(session.status) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = runningSessions,
            key = { session -> session.id },
        ) { session ->
            SessionCard(
                session = session,
                onClick = {
                    onOpenSession(session.id)
                },
                onDelete = {
                    onDeleteSession(session.id)
                },
            )
        }

        if (runningSessions.isNotEmpty() && otherSessions.isNotEmpty()) {
            item(key = "running-divider") {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
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
                    onOpenSession(session.id)
                },
                onDelete = {
                    onDeleteSession(session.id)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    filter: String?,
    onFilterSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text("IMbot")
        },
        actions = {
            Box {
                TextButton(
                    onClick = {
                        expanded = true
                    },
                ) {
                    Text(filterLabel(filter))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    },
                ) {
                    providerFilterOptions().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(option.label)
                            },
                            onClick = {
                                expanded = false
                                onFilterSelected(option.value)
                            },
                        )
                    }
                }
            }
        },
    )
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
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(112.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
                    ShimmerSkeleton(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShimmerSkeleton(modifier = Modifier.weight(1f).height(14.dp))
                        ShimmerSkeleton(modifier = Modifier.size(12.dp), shape = MaterialTheme.shapes.small)
                    }
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.extraLarge,
                ),
    )
}

private fun filterLabel(filter: String?): String =
    providerFilterOptions()
        .firstOrNull { option -> option.value == filter }
        ?.label
        ?: "全部"

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
