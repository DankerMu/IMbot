@file:Suppress("FunctionName")

package com.imbot.android.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.ui.components.StatusIndicator
import com.imbot.android.ui.components.StatusIndicatorVariant
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalProviderColors
import com.imbot.android.ui.theme.providerColorFor

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: SessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    allowDelete: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(session.id) { mutableStateOf(false) }
    val componentShapes = LocalIMbotComponentShapes.current
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (allowDelete && value == SwipeToDismissBoxValue.EndToStart) {
                    showDeleteDialog = true
                }
                false
            },
        )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
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
            title = {
                Text("确认删除此会话？")
            },
            text = {
                Text("该操作会同步删除本地缓存，并调用 Relay 的归档接口。")
            },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = allowDelete,
        backgroundContent = {
            if (allowDelete) {
                DeleteBackground()
            }
        },
        modifier = modifier,
    ) {
        Box {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = {
                                if (allowDelete) {
                                    showContextMenu = true
                                }
                            },
                        ),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = componentShapes.card,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProviderBadge(provider = session.provider)
                            Text(
                                text = providerDisplayName(session.provider),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = formatRelativeTime(session.lastActiveAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = summarizeWorkspacePath(session.workspaceCwd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summarizePrompt(session.initialPrompt),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusIndicator(
                            status = session.status,
                            variant = StatusIndicatorVariant.Dot,
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = {
                    showContextMenu = false
                },
            ) {
                if (allowDelete) {
                    DropdownMenuItem(
                        text = {
                            Text("归档")
                        },
                        onClick = {
                            showContextMenu = false
                            showDeleteDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("删除")
                        },
                        onClick = {
                            showContextMenu = false
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteBackground() {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFD64545))
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun ProviderBadge(provider: String) {
    val badgeColor = providerColorFor(provider = provider, colors = LocalProviderColors.current)

    Box(
        modifier =
            Modifier
                .size(42.dp)
                .background(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = providerShortLabel(provider),
            style = MaterialTheme.typography.labelMedium,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun providerDisplayName(provider: String): String =
    when (provider) {
        "claude" -> "Claude Code"
        "book" -> "book"
        "openclaw" -> "OpenClaw"
        else -> provider
    }

private fun providerShortLabel(provider: String): String =
    when (provider) {
        "claude" -> "CC"
        "book" -> "BK"
        "openclaw" -> "OC"
        else -> provider.take(2).uppercase()
    }
