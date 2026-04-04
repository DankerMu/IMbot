@file:Suppress("FunctionName")

package com.imbot.android.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.newsession.DirectoryBreadcrumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRootBottomSheet(
    state: AddRootUiState,
    onDismiss: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onBrowseDirectory: (String) -> Unit,
    onBrowseUp: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "添加根目录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProviderChip(
                    label = "Claude",
                    provider = "claude",
                    selected = state.provider == "claude",
                    onClick = onProviderSelected,
                )
                ProviderChip(
                    label = "book",
                    provider = "book",
                    selected = state.provider == "book",
                    onClick = onProviderSelected,
                )
                ProviderChip(
                    label = "OpenClaw",
                    provider = "openclaw",
                    selected = state.provider == "openclaw",
                    onClick = onProviderSelected,
                )
            }

            Text(
                text = "主机: ${state.hostName ?: "未选择"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BreadcrumbRow(
                    breadcrumbs = state.breadcrumbs,
                    onBrowseDirectory = onBrowseDirectory,
                )
                TextButton(
                    onClick = onBrowseUp,
                    enabled = state.breadcrumbs.size > 1 && !state.isLoading,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                    Text("上一级")
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 320.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(12.dp),
                ) {
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        state.error != null -> {
                            Text(
                                text = state.error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        state.directories.isEmpty() -> {
                            Text(
                                text = "此目录下暂无子目录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = state.directories,
                                    key = { entry -> entry.path },
                                ) { entry ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onBrowseDirectory(entry.path)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(12.dp),
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(entry.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = entry.path,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "当前目录: ${state.currentPath.ifBlank { "未选择" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.warning?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.label,
                onValueChange = onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("显示名称")
                },
                placeholder = {
                    Text("默认使用目录名")
                },
                singleLine = true,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onSubmit,
                    enabled =
                        state.provider != null &&
                            state.hostId != null &&
                            state.currentPath.isNotBlank() &&
                            !state.isSubmitting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    provider: String,
    selected: Boolean,
    onClick: (String) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = {
            onClick(provider)
        },
        label = {
            Text(label)
        },
        leadingIcon = {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(
                            color = providerColor(provider),
                            shape = CircleShape,
                        ),
            )
        },
    )
}

@Composable
private fun BreadcrumbRow(
    breadcrumbs: List<DirectoryBreadcrumb>,
    onBrowseDirectory: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, breadcrumb ->
            TextButton(
                onClick = {
                    onBrowseDirectory(breadcrumb.path)
                },
            ) {
                Text(breadcrumb.label)
            }
            if (index < breadcrumbs.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun providerColor(provider: String): Color =
    when (provider) {
        "claude" -> Color(0xFFFFB74D)
        "book" -> Color(0xFF8E6AD9)
        "openclaw" -> Color(0xFFE57373)
        else -> Color.Gray
    }
