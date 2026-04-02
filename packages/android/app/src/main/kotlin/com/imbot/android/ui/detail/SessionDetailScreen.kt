@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.LocalSnackbarHostState
import com.imbot.android.ui.components.StatusIndicator
import com.imbot.android.ui.components.StatusIndicatorVariant
import com.imbot.android.ui.theme.IMbotAnimations
import com.imbot.android.ui.theme.LocalProviderColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun SessionDetailScreen(
    viewModel: DetailViewModel,
    errorState: ErrorState,
    sessionId: String,
    onNavigateBack: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val messageMenuTarget = uiState.messageMenuTarget
    val messageActions = messageMenuTarget?.let(::availableActions).orEmpty()

    var menuExpanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var initialLoadHandled by rememberSaveable(sessionId) { mutableStateOf(false) }
    val renderedKeys = remember(sessionId) { mutableSetOf<String>() }

    BackHandler(enabled = uiState.selectionModeMessageId != null) {
        viewModel.onExitSelectionMode()
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        viewModel.clearError()
    }

    LaunchedEffect(uiState.messages) {
        if (uiState.messages.isEmpty()) {
            return@LaunchedEffect
        }
        renderedKeys.addAll(uiState.messages.map(::timelineKey))
        if (!initialLoadHandled) {
            initialLoadHandled = true
        }
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

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showCompleteDialog = false
            },
            title = {
                Text("结束当前会话？")
            },
            text = {
                Text("Relay 会结束当前空闲会话，结束后将无法继续对话。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompleteDialog = false
                        viewModel.completeSession()
                    },
                ) {
                    Text("结束会话")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCompleteDialog = false
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

    if (messageMenuTarget != null && messageActions.isNotEmpty()) {
        MessageActionSheet(
            actions = messageActions,
            onDismiss = viewModel::onDismissMessageMenu,
            onAction = { action ->
                when (action) {
                    is MessageAction.CopyMessage -> {
                        clipboardManager.setText(AnnotatedString(action.text))
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onDismissMessageMenu()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "已复制到剪贴板",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }

                    MessageAction.SelectText -> {
                        val messageId =
                            when (messageMenuTarget) {
                                is MessageItem.AgentMessage -> messageMenuTarget.id
                                is MessageItem.UserMessage -> messageMenuTarget.id
                                else -> null
                            }
                        if (messageId != null) {
                            viewModel.onEnterSelectionMode(messageId)
                        } else {
                            viewModel.onDismissMessageMenu()
                        }
                    }
                }
            },
        )
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        DetailTopBarTitle(
                            title = uiState.session?.let(::sessionTitle) ?: "会话详情",
                            subtitle = uiState.session?.let(::sessionSubtitle),
                            provider = uiState.session?.provider.orEmpty(),
                        )
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
                            StatusIndicator(
                                status = session.status,
                                variant = StatusIndicatorVariant.Badge,
                            )
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
                            if (canCancelSession(uiState.session?.status)) {
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
                            if (canResumeSession(uiState.session?.status)) {
                                DropdownMenuItem(
                                    text = {
                                        Text("恢复会话")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = !uiState.isResuming,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.resumeSession()
                                    },
                                )
                            }
                            if (canCompleteSession(uiState.session?.status)) {
                                DropdownMenuItem(
                                    text = {
                                        Text("结束会话")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = !uiState.isCompleting,
                                    onClick = {
                                        menuExpanded = false
                                        showCompleteDialog = true
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
                                        AnnotatedString(
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
                    ErrorBannerHost(
                        errorState = errorState,
                        scope = ErrorScope.SESSION(sessionId),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        hostId = uiState.session?.hostId,
                    )
                    StatusIndicator(
                        status = uiState.session?.status.orEmpty(),
                        variant = StatusIndicatorVariant.Bar,
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
                            ) { index, item ->
                                val key = timelineKey(item)
                                val shouldStaggerInitial =
                                    !initialLoadHandled && index < IMbotAnimations.STAGGER_ITEM_LIMIT
                                val shouldAnimateNew = initialLoadHandled && key !in renderedKeys

                                AnimatedTimelineEntry(
                                    itemKey = key,
                                    shouldAnimateOnEnter = shouldStaggerInitial || shouldAnimateNew,
                                    enterDelayMs =
                                        if (shouldStaggerInitial) {
                                            index * IMbotAnimations.STAGGER_DELAY_MS
                                        } else {
                                            0
                                        },
                                ) {
                                    when (item) {
                                        is MessageItem.ToolCall ->
                                            ToolCallCard(
                                                item = item,
                                                onLongPress = viewModel::onMessageLongPress,
                                            )

                                        else ->
                                            MessageBubble(
                                                item = item,
                                                provider = uiState.session?.provider.orEmpty(),
                                                onLongPress = viewModel::onMessageLongPress,
                                                isSelectionMode =
                                                    isSelectionMode(
                                                        item = item,
                                                        selectionModeMessageId = uiState.selectionModeMessageId,
                                                    ),
                                            )
                                    }
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
}

@Composable
private fun DetailTopBarTitle(
    title: String,
    subtitle: String?,
    provider: String,
) {
    Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarProviderBadge(provider = provider)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            subtitle?.let { subtitleText ->
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopBarProviderBadge(provider: String) {
    val badgeColor = providerColor(provider, LocalProviderColors.current)
    val label = providerShortLabel(provider).ifBlank { "IM" }

    Box(
        modifier =
            Modifier
                .size(36.dp)
                .background(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AnimatedTimelineEntry(
    itemKey: String,
    shouldAnimateOnEnter: Boolean,
    enterDelayMs: Int,
    content: @Composable () -> Unit,
) {
    val slideOffsetPx = with(LocalDensity.current) { IMbotAnimations.MESSAGE_OFFSET_DP.dp.roundToPx() }
    var visible by rememberSaveable(itemKey) { mutableStateOf(!shouldAnimateOnEnter) }

    LaunchedEffect(itemKey, shouldAnimateOnEnter, enterDelayMs) {
        if (!shouldAnimateOnEnter) {
            visible = true
            return@LaunchedEffect
        }

        visible = false
        if (enterDelayMs > 0) {
            delay(enterDelayMs.toLong())
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                        easing = IMbotAnimations.standardEasing,
                    ),
            ) +
                slideInVertically(
                    animationSpec =
                        tween(
                            durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                            easing = IMbotAnimations.standardEasing,
                        ),
                    initialOffsetY = { slideOffsetPx },
                ),
        exit = ExitTransition.None,
        label = "timeline-entry-$itemKey",
    ) {
        content()
    }
}

private fun timelineKey(item: MessageItem): String =
    when (item) {
        is MessageItem.AgentMessage -> "agent-${item.id}"
        is MessageItem.StatusChange -> "status-${item.id}"
        is MessageItem.ToolCall -> "tool-${item.callId}"
        is MessageItem.UserMessage -> "user-${item.id}"
    }

private fun isSelectionMode(
    item: MessageItem,
    selectionModeMessageId: String?,
): Boolean =
    when (item) {
        is MessageItem.AgentMessage -> item.id == selectionModeMessageId
        is MessageItem.UserMessage -> item.id == selectionModeMessageId
        is MessageItem.StatusChange,
        is MessageItem.ToolCall,
        -> false
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
