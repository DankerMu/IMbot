@file:Suppress("FunctionName", "TooManyFunctions")

package com.imbot.android.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.LocalSnackbarHostState
import com.imbot.android.ui.components.StatusIndicator
import com.imbot.android.ui.components.StatusIndicatorVariant
import com.imbot.android.ui.theme.CodeFontFamily
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

    val messageMenuTarget = uiState.messageMenuTarget
    val selectionModeMessageId = uiState.selectionModeMessageId
    val selectionModeActive = selectionModeMessageId != null
    val effectiveStatus = uiState.effectiveStatus ?: uiState.session?.status
    val messageActions = messageMenuTarget?.takeIf(::hasActions)?.let(::availableActions).orEmpty()
    val sessionAllowsInteractiveInput = canRespondToInteractiveRequest(effectiveStatus)
    val showUsageIndicator = effectiveStatus == "running" || effectiveStatus == "idle"
    val latestPendingInteractiveCallId = findLatestPendingInteractiveToolCallId(uiState.messages, effectiveStatus)
    val latestPendingApprovalCallId = findLatestPendingApprovalCallId(uiState.messages, effectiveStatus)
    val timelineMessages = remember(uiState.messages) { deduplicateStatusChanges(uiState.messages) }
    val displayedTimelineLastIndex by rememberUpdatedState(timelineMessages.lastIndex)

    var menuExpanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var initialLoadHandled by rememberSaveable(sessionId) { mutableStateOf(false) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    val renderedKeys = remember(sessionId) { mutableSetOf<String>() }

    BackHandler(enabled = selectionModeActive) {
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

    LaunchedEffect(timelineMessages) {
        if (timelineMessages.isEmpty()) {
            return@LaunchedEffect
        }
        renderedKeys.addAll(timelineMessages.map(::timelineKey))
        if (!initialLoadHandled) {
            initialLoadHandled = true
            listState.scrollToItem(timelineMessages.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        var lastPosition =
            ListViewportPosition(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
            )
        snapshotFlow {
            ScrollObservation(
                nearBottom = isNearBottom(listState),
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { observation ->
                val currentPosition =
                    ListViewportPosition(
                        index = observation.firstVisibleItemIndex,
                        offset = observation.firstVisibleItemScrollOffset,
                    )
                val userInitiatedScrollAway =
                    currentPosition != lastPosition &&
                        !programmaticScrollInProgress &&
                        !observation.nearBottom

                if (observation.nearBottom || userInitiatedScrollAway) {
                    viewModel.onScrollPositionChanged(
                        nearBottom = observation.nearBottom,
                        userInitiatedScrollAway = userInitiatedScrollAway,
                    )
                }

                lastPosition = currentPosition
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DetailEvent.ScrollToBottom -> {
                    val targetIndex =
                        if (event.targetIndex > displayedTimelineLastIndex) {
                            displayedTimelineLastIndex
                        } else {
                            event.targetIndex
                        }
                    if (targetIndex >= 0) {
                        snapshotFlow { listState.layoutInfo.totalItemsCount }
                            .first { count -> count > targetIndex }
                        try {
                            programmaticScrollInProgress = true
                            listState.animateScrollToItem(targetIndex)
                            alignTargetItemBottom(listState, targetIndex)
                        } finally {
                            programmaticScrollInProgress = false
                            viewModel.onScrollPositionChanged(
                                nearBottom = isNearBottom(listState),
                                userInitiatedScrollAway = false,
                            )
                        }
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

    if (uiState.showSlashSheet) {
        SlashCommandSheet(
            onDismiss = viewModel::onDismissSlashSheet,
            onSkillSelected = viewModel::onSkillSelected,
        )
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        DetailSoftInputModeEffect(softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    SessionDetailHeader(
                        subtitle = uiState.session?.let(::sessionSubtitle),
                        provider = uiState.session?.provider.orEmpty(),
                        usage = uiState.usage,
                        showUsageIndicator = showUsageIndicator,
                        status = effectiveStatus.orEmpty(),
                        onNavigateBack = {
                            if (selectionModeActive) {
                                viewModel.onExitSelectionMode()
                            } else {
                                onNavigateBack(false)
                            }
                        },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = {
                                        menuExpanded = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = "更多操作",
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = {
                                        menuExpanded = false
                                    },
                                ) {
                                    if (canCancelSession(effectiveStatus)) {
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
                                    if (canResumeSession(effectiveStatus)) {
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
                                    if (canCompleteSession(effectiveStatus)) {
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
                            }
                        },
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    )
                }
                ErrorBannerHost(
                    errorState = errorState,
                    scope = ErrorScope.SESSION(sessionId),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    hostId = uiState.session?.hostId,
                )

                Box(
                    modifier = Modifier.weight(1f),
                ) {
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
                            contentPadding =
                                PaddingValues(
                                    horizontal = MESSAGE_HORIZONTAL_PADDING,
                                    vertical = MESSAGE_VERTICAL_PADDING,
                                ),
                        ) {
                            itemsIndexed(
                                items = timelineMessages,
                                key = { _, item -> timelineKey(item) },
                            ) { index, item ->
                                val key = timelineKey(item)
                                val spacing =
                                    if (index == 0) {
                                        0.dp
                                    } else {
                                        messageSpacing(timelineMessages.getOrNull(index - 1), item)
                                    }
                                val isItemInSelectionMode =
                                    isSelectionMode(
                                        item = item,
                                        selectionModeMessageId = selectionModeMessageId,
                                    )
                                val shouldStaggerInitial =
                                    !initialLoadHandled && index < IMbotAnimations.STAGGER_ITEM_LIMIT
                                val shouldAnimateNew = initialLoadHandled && key !in renderedKeys
                                val isToolTimelineItem =
                                    item is MessageItem.ToolCall || item is MessageItem.InteractiveToolCall
                                val itemDismissModifier =
                                    if (
                                        selectionModeActive &&
                                        !isItemInSelectionMode &&
                                        !isToolTimelineItem
                                    ) {
                                        Modifier.clickable(onClick = viewModel::onExitSelectionMode)
                                    } else {
                                        Modifier
                                    }

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
                                    Box(modifier = Modifier.padding(top = spacing)) {
                                        when (item) {
                                            is MessageItem.InteractiveToolCall ->
                                                InteractiveToolCard(
                                                    item = item,
                                                    sessionStatus = effectiveStatus,
                                                    isSessionActive = sessionAllowsInteractiveInput,
                                                    isLatestPending =
                                                        isLatestPendingInteractiveToolCall(
                                                            item = item,
                                                            latestPendingCallId = latestPendingInteractiveCallId,
                                                        ),
                                                    isSending = uiState.isSending,
                                                    onSubmitAnswer = { answer ->
                                                        viewModel.submitToolAnswer(item.id, answer)
                                                    },
                                                    onLongPress = viewModel::onMessageLongPress,
                                                    selectionModeActive = selectionModeActive,
                                                    onExitSelectionMode = viewModel::onExitSelectionMode,
                                                )

                                            is MessageItem.ToolCall ->
                                                ToolCallCard(
                                                    item = item,
                                                    onLongPress = viewModel::onMessageLongPress,
                                                    selectionModeActive = selectionModeActive,
                                                    onExitSelectionMode = viewModel::onExitSelectionMode,
                                                )

                                            else ->
                                                MessageBubble(
                                                    item = item,
                                                    provider = uiState.session?.provider.orEmpty(),
                                                    isSessionActive = sessionAllowsInteractiveInput,
                                                    isLatestPendingApproval =
                                                        (item as? MessageItem.StatusChange)?.let { statusChange ->
                                                            isLatestPendingApprovalRequest(
                                                                item = statusChange,
                                                                latestPendingCallId = latestPendingApprovalCallId,
                                                            )
                                                        } ?: true,
                                                    isSending = uiState.isSending,
                                                    onApprove = {
                                                        val callId = (item as? MessageItem.StatusChange)?.callId
                                                        if (callId.isNullOrBlank()) {
                                                            viewModel.approveToolCall()
                                                        } else {
                                                            viewModel.approveToolCall(callId)
                                                        }
                                                    },
                                                    onDeny = {
                                                        val callId = (item as? MessageItem.StatusChange)?.callId
                                                        if (callId.isNullOrBlank()) {
                                                            viewModel.denyToolCall()
                                                        } else {
                                                            viewModel.denyToolCall(callId)
                                                        }
                                                    },
                                                    onLongPress = viewModel::onMessageLongPress,
                                                    selectionModeActive = selectionModeActive,
                                                    onExitSelectionMode = viewModel::onExitSelectionMode,
                                                    isSelectionMode = isItemInSelectionMode,
                                                    modifier = itemDismissModifier,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.scrollState.fabVisible,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 20.dp),
                        enter =
                            fadeIn(animationSpec = tween(durationMillis = 150)) +
                                scaleIn(
                                    animationSpec = tween(durationMillis = 150),
                                    initialScale = 0.92f,
                                ),
                        exit =
                            fadeOut(animationSpec = tween(durationMillis = 150)) +
                                scaleOut(
                                    animationSpec = tween(durationMillis = 150),
                                    targetScale = 0.92f,
                                ),
                    ) {
                        ScrollToBottomButton(
                            count = uiState.scrollState.newMsgCount,
                            onClick = viewModel::onFabTapped,
                        )
                    }
                }

                InputBar(
                    status = effectiveStatus,
                    canSend = uiState.canSend,
                    isSending = uiState.isSending,
                    commandChip = uiState.commandChip,
                    onSlashTrigger = viewModel::onSlashTrigger,
                    onDismissCommand = viewModel::onDismissCommand,
                    onSend = viewModel::sendMessage,
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun DetailSoftInputModeEffect(softInputMode: Int) {
    val view = LocalView.current
    DisposableEffect(view, softInputMode) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val previousMode = window?.attributes?.softInputMode
        if (window != null) {
            window.setSoftInputMode(softInputMode)
        }
        onDispose {
            if (window != null && previousMode != null) {
                window.setSoftInputMode(previousMode)
            }
        }
    }
}

@Composable
private fun SessionDetailHeader(
    subtitle: String?,
    provider: String,
    usage: SessionUsageState,
    showUsageIndicator: Boolean,
    status: String,
    onNavigateBack: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val providerLabel = providerDisplayName(provider).ifBlank { "会话详情" }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarProviderBadge(provider = provider)
                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TopBarStatusBadge(
                status = status,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
            actions()
        }
        DetailTopBarInfo(
            subtitle = subtitle,
            usage = usage,
            showUsageIndicator = showUsageIndicator,
            modifier = Modifier.padding(start = 56.dp, end = 8.dp),
        )
    }
}

@Composable
private fun DetailTopBarInfo(
    subtitle: String?,
    usage: SessionUsageState,
    showUsageIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    if (subtitle == null && (!showUsageIndicator || usage.totalTokens <= 0)) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subtitle?.let { subtitleText ->
            TopBarMetaPill(
                text = subtitleText,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        UsageIndicator(
            usage = usage,
            isActive = showUsageIndicator,
        )
    }
}

@Composable
private fun TopBarMetaPill(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CodeFontFamily,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UsageIndicator(
    usage: SessionUsageState,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isActive || usage.totalTokens == 0 || usage.contextWindow == 0) {
        return
    }

    val color = usageColor(usage.usagePercent)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${formatTokenCount(usage.totalTokens)} / ${formatTokenCount(usage.contextWindow)}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { usage.usagePercent.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun TopBarStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    if (status.isBlank()) {
        return
    }

    StatusIndicator(
        status = status,
        variant = StatusIndicatorVariant.Badge,
        modifier = modifier,
    )
}

@Composable
private fun TopBarProviderBadge(provider: String) {
    val badgeColor = providerColor(provider, LocalProviderColors.current)
    val label = providerShortLabel(provider).ifBlank { "IM" }

    Box(
        modifier =
            Modifier
                .size(28.dp)
                .background(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
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
private fun ScrollToBottomButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            modifier =
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onClick),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "回到底部",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (count > 0) {
            UnreadBadge(count = count)
        }
    }
}

@Composable
private fun BoxScope.UnreadBadge(count: Int) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 0.dp)
                .offset(x = 4.dp, y = (-4).dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                    ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
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
                animationSpec = IMbotAnimations.GentleSpring,
            ) +
                slideInVertically(
                    animationSpec =
                        spring(
                            dampingRatio = IMbotAnimations.DefaultSpring.dampingRatio,
                            stiffness = IMbotAnimations.DefaultSpring.stiffness,
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
        is MessageItem.InteractiveToolCall -> "interactive-tool-${item.id}"
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
        is MessageItem.InteractiveToolCall -> false
        is MessageItem.UserMessage -> item.id == selectionModeMessageId
        is MessageItem.StatusChange,
        is MessageItem.ToolCall,
        -> false
    }

private fun isNearBottom(listState: androidx.compose.foundation.lazy.LazyListState): Boolean {
    val layoutInfo = listState.layoutInfo
    val lastVisible =
        layoutInfo.visibleItemsInfo.lastOrNull()
            ?: return layoutInfo.totalItemsCount == 0
    val isLastItemVisible = lastVisible.index >= layoutInfo.totalItemsCount - 1
    val overflow = lastVisible.offset + lastVisible.size - layoutInfo.viewportEndOffset
    return isLastItemVisible && overflow <= NEAR_BOTTOM_TOLERANCE_PX
}

private const val NEAR_BOTTOM_TOLERANCE_PX = 80

private data class ScrollObservation(
    val nearBottom: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private data class ListViewportPosition(
    val index: Int,
    val offset: Int,
)

private suspend fun alignTargetItemBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetIndex: Int,
) {
    repeat(3) {
        val layoutInfo = listState.layoutInfo
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return
        val overflow = targetItem.offset + targetItem.size - layoutInfo.viewportEndOffset
        if (overflow <= NEAR_BOTTOM_TOLERANCE_PX) {
            return
        }
        listState.animateScrollBy(overflow.toFloat())
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
