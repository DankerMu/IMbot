@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.network.BrowseEntry
import com.imbot.android.network.RelayWorkspaceRoot
import com.imbot.android.ui.newsession.DirectoryBreadcrumb
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.SurfaceTertiary
import com.imbot.android.ui.theme.SurfaceTertiaryDark
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow

@Suppress("CyclomaticComplexMethod")
@Composable
fun DirectoryBrowser(
    hostId: String,
    roots: List<RelayWorkspaceRoot>,
    browsePath: String?,
    breadcrumbs: List<DirectoryBreadcrumb>,
    browseEntries: List<BrowseEntry>,
    selectedPath: String?,
    onBrowse: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val currentPath = browsePath
    val showingRoots = currentPath == null
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        ) {
            Text(
                text = "Host: $hostId",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!showingRoots) {
            BreadcrumbBar(
                breadcrumbs = breadcrumbs,
                onBrowse = onBrowse,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) {
            when {
                isLoading -> DirectoryLoadingState(modifier = Modifier.fillMaxSize())
                error != null -> DirectoryErrorState(message = error, onRetry = onRetry)
                showingRoots && roots.isEmpty() -> DirectoryEmptyState(message = "暂无可用根目录")
                !showingRoots && browseEntries.isEmpty() -> DirectoryEmptyState(message = "无子目录")
                showingRoots -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = roots,
                            key = { root -> root.id },
                        ) { root ->
                            DirectoryEntryCard(
                                title = root.label ?: root.path.substringAfterLast("/"),
                                subtitle = root.path,
                                selected = selectedPath == root.path,
                                onClick = {
                                    onBrowse(root.path)
                                },
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = browseEntries,
                            key = { entry -> entry.path },
                        ) { entry ->
                            DirectoryEntryCard(
                                title = entry.name,
                                subtitle = entry.path,
                                selected = selectedPath == entry.path,
                                onClick = {
                                    onBrowse(entry.path)
                                },
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                currentPath?.let(onSelect)
            },
            enabled = !currentPath.isNullOrBlank() && !isLoading && error == null,
            modifier = Modifier.fillMaxWidth(),
            shape = componentShapes.button,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = if (isDarkTheme) SurfaceTertiaryDark else SurfaceTertiary,
                    disabledContentColor = MaterialTheme.colorScheme.outline,
                ),
        ) {
            if (currentPath != null && currentPath == selectedPath) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                )
            }
            Text(
                text =
                    if (currentPath != null && currentPath == selectedPath) {
                        "已选择此目录"
                    } else {
                        "选择此目录"
                    },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<DirectoryBreadcrumb>,
    onBrowse: (String) -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, breadcrumb ->
            Surface(
                shape = componentShapes.button,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier =
                    Modifier.clickable {
                        onBrowse(breadcrumb.path)
                    },
            ) {
                Text(
                    text = breadcrumb.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (index < breadcrumbs.lastIndex) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DirectoryEntryCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .appleChrome(
                    shape = componentShapes.card,
                    isDarkTheme = isDarkTheme,
                    outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shadowTokens = shadowTokens,
                )
                .clickable(onClick = onClick),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        shape = componentShapes.card,
        border =
            if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
            } else {
                null
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title.ifBlank { subtitle.substringAfterLast("/") },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DirectoryLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(5) {
            ShimmerSkeleton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}

@Composable
private fun DirectoryErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InlineRetry(
                errorMessage = message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun DirectoryEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
