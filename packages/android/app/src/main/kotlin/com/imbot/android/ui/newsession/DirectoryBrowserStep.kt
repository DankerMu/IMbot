@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.components.DirectoryBrowser

@Composable
fun DirectoryBrowserStep(
    state: NewSessionUiState,
    onBrowse: (String) -> Unit,
    onRetry: () -> Unit,
    onSelectDirectory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostId = state.hostId
    if (hostId == null) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "请先返回上一步选择 Provider。",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "选择目录",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "先进入根目录，再在其中挑选最终工作目录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DirectoryBrowser(
            hostId = hostId,
            roots = state.roots,
            browsePath = state.browsePath,
            breadcrumbs = state.breadcrumbs,
            browseEntries = state.browseEntries,
            selectedPath = state.cwd,
            onBrowse = onBrowse,
            onSelect = onSelectDirectory,
            onRetry = onRetry,
            isLoading = state.isLoadingRoots || state.isLoadingBrowse,
            error = state.directoryError,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )

        if (state.cwd != null) {
            Text(
                text = "当前选择: ${state.cwd}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
