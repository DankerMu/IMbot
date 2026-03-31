@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun SessionDetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var menuExpanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(listState, density) {
        snapshotFlow {
            with(density) {
                calculateDistanceFromBottomPx(listState).toDp().value
            }
        }
            .distinctUntilChanged()
            .collect { distanceFromBottomDp ->
                viewModel.onScrollPositionChanged(distanceFromBottomDp)
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DetailEvent.ScrollToBottom -> {
                    if (event.targetIndex >= 0) {
                        snapshotFlow { listState.layoutInfo.totalItemsCount }
                            .first { count -> count > event.targetIndex }
                        listState.animateScrollToItem(event.targetIndex)
                    }
                }

                is DetailEvent.NavigateBack -> onNavigateBack(event.refreshHome)
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = {
                showCancelDialog = false
            },
            title = {
                Text("取消当前会话？")
            },
            text = {
                Text("Relay 会尝试停止正在运行的任务。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelSession()
                    },
                ) {
                    Text("取消会话")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                    },
                ) {
                    Text("保留")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("删除此会话？")
            },
            text = {
                Text("该操作会调用 Relay 删除接口，并在返回列表后刷新会话列表。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSession()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.session?.let(::sessionTitle) ?: "会话详情",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        uiState.session?.let { session ->
                            Text(
                                text = sessionSubtitle(session),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onNavigateBack(false)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    uiState.session?.let { session ->
                        StatusBadge(status = session.status)
                    }
                    IconButton(
                        onClick = {
                            menuExpanded = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                        },
                    ) {
                        if (canSendToSession(uiState.session?.status)) {
                            DropdownMenuItem(
                                text = {
                                    Text("取消会话")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.StopCircle,
                                        contentDescription = null,
                                    )
                                },
                                enabled = !uiState.isCancelling,
                                onClick = {
                                    menuExpanded = false
                                    showCancelDialog = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text("删除会话")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                )
                            },
                            enabled = !uiState.isDeleting,
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text("复制全部输出")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                clipboardManager.setText(
                                    androidx.compose.ui.text.AnnotatedString(
                                        copyableAgentTranscript(uiState.messages),
                                    ),
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("已复制全部输出")
                                }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                status = uiState.session?.status,
                canSend = uiState.canSend,
                isSending = uiState.isSending,
                onSend = viewModel::sendMessage,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                uiState.connectionBanner?.let { banner ->
                    ConnectionBanner(
                        state = banner,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                DetailStatusBar(
                    status = uiState.session?.status.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.isLoading && uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, item -> timelineKey(item) },
                        ) { _, item ->
                            when (item) {
                                is MessageItem.ToolCall ->
                                    ToolCallCard(
                                        item = item,
                                    )

                                else ->
                                    MessageBubble(
                                        item = item,
                                        provider = uiState.session?.provider.orEmpty(),
                                    )
                            }
                        }
                    }
                }
            }

            if (uiState.scrollState.fabVisible) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onFabTapped,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                        )
                    },
                    text = {
                        val count = uiState.scrollState.newMsgCount
                        Text(if (count > 0) "↓ $count 条新消息" else "回到底部")
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 88.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    Surface(
        color = detailStatusColor(status).copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = detailStatusColor(status),
        )
    }
}

@Composable
private fun ConnectionBanner(
    state: ConnectionBannerUiState,
    modifier: Modifier = Modifier,
) {
    val background =
        if (state.isSuccess) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        if (state.isSuccess) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

    Surface(
        modifier = modifier,
        color = background,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = state.message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun DetailStatusBar(
    status: String,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(detailStatusColor(status), label = "detail-status-color")
    val alpha =
        if (status == "running") {
            val transition = rememberInfiniteTransition(label = "detail-status-pulse")
            val pulse by
                transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 750, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "detail-status-alpha",
                )
            pulse
        } else {
            animateFloatAsState(targetValue = 1f, label = "detail-status-static").value
        }

    Box(
        modifier =
            modifier
                .height(2.dp)
                .background(color.copy(alpha = alpha)),
    )
}

private fun timelineKey(item: MessageItem): String =
    when (item) {
        is MessageItem.AgentMessage -> "agent-${item.id}"
        is MessageItem.StatusChange -> "status-${item.id}"
        is MessageItem.ToolCall -> "tool-${item.callId}"
        is MessageItem.UserMessage -> "user-${item.id}"
    }

private fun calculateDistanceFromBottomPx(listState: androidx.compose.foundation.lazy.LazyListState): Int {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val lastVisible = visibleItems.lastOrNull()

    return if (layoutInfo.totalItemsCount == 0 || lastVisible == null) {
        0
    } else {
        val itemsBelow = (layoutInfo.totalItemsCount - lastVisible.index - 1).coerceAtLeast(0)
        val averageSize = visibleItems.map { it.size }.average().takeIf { it.isFinite() } ?: 0.0
        val hiddenBelowLastVisible =
            (lastVisible.offset + lastVisible.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)

        (itemsBelow * averageSize + hiddenBelowLastVisible).toInt()
    }
}
