@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.components.ErrorBannerHost
import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.SurfaceTertiary
import com.imbot.android.ui.theme.SurfaceTertiaryDark
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(
    viewModel: NewSessionViewModel,
    errorState: ErrorState,
    onNavigateBack: () -> Unit,
    onSessionCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { STEP_TITLES.size })
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    LaunchedEffect(uiState.step) {
        pagerState.animateScrollToPage(uiState.step)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is NewSessionEvent.SessionCreated -> onSessionCreated(event.sessionId)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("新建会话")
                        Text(
                            text = "Launch a remote session",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        bottomBar = {
            WizardNavigationBar(
                step = uiState.step,
                canMoveNext = canMoveNext(uiState),
                canCreate = canCreate(uiState),
                isCreating = uiState.isCreating,
                onPrevious = {
                    viewModel.goToStep(uiState.step - 1)
                },
                onNext = {
                    viewModel.goToStep(uiState.step + 1)
                },
                onCreate = viewModel::createSession,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorBannerHost(
                errorState = errorState,
                scope = ErrorScope.WORKSPACE,
                modifier = Modifier.fillMaxWidth(),
                hostId = newSessionBannerHostId(uiState),
            )
            StepIndicator(
                currentStep = uiState.step,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .appleChrome(
                            shape = componentShapes.card,
                            isDarkTheme = isDarkTheme,
                            outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                            shadowTokens = shadowTokens,
                        ),
                shape = componentShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 ->
                            ProviderPickerStep(
                                hosts = uiState.hosts,
                                selectedProvider = uiState.provider,
                                isLoading = uiState.isLoadingHosts,
                                error = uiState.error,
                                onSelectProvider = viewModel::selectProvider,
                                onRetry = viewModel::loadHosts,
                            )

                        1 ->
                            DirectoryBrowserStep(
                                state = uiState,
                                onBrowse = viewModel::browseDirectory,
                                onRetry = {
                                    val pending = uiState.pendingBrowsePath
                                    if (!pending.isNullOrBlank()) {
                                        viewModel.browseDirectory(pending)
                                    } else if (uiState.browsePath.isNullOrBlank()) {
                                        viewModel.loadRoots()
                                    } else {
                                        viewModel.browseDirectory(uiState.browsePath!!)
                                    }
                                },
                                onSelectDirectory = viewModel::selectDirectory,
                            )

                        else ->
                            PromptInputStep(
                                provider = uiState.provider,
                                cwd = uiState.cwd,
                                prompt = uiState.prompt,
                                model = uiState.model,
                                onPromptChanged = viewModel::updatePrompt,
                                onModelChanged = viewModel::updateModel,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        STEP_TITLES.forEachIndexed { index, title ->
            val active = index == currentStep
            val complete = index < currentStep
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                color =
                    when {
                        active -> MaterialTheme.colorScheme.primaryContainer
                        complete -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                border =
                    BorderStroke(
                        1.dp,
                        when {
                            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                            complete -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        },
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepDot(
                        index = index,
                        active = active,
                        complete = complete,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDot(
    index: Int,
    active: Boolean,
    complete: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .border(
                    width = 1.dp,
                    color =
                        if (active || complete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    shape = CircleShape,
                )
                .background(
                    color =
                        if (active || complete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (active || complete) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun WizardNavigationBar(
    step: Int,
    canMoveNext: Boolean,
    canCreate: Boolean,
    isCreating: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val primaryButtonColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (isDarkTheme) SurfaceTertiaryDark else SurfaceTertiary,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = if (isDarkTheme) 0.dp else 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                Button(
                    onClick = onPrevious,
                    enabled = !isCreating,
                    modifier = Modifier.weight(1f),
                    shape = componentShapes.button,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Text("上一步")
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }

            if (step < STEP_TITLES.lastIndex) {
                Button(
                    onClick = onNext,
                    enabled = canMoveNext && !isCreating,
                    modifier = Modifier.weight(1f),
                    shape = componentShapes.button,
                    colors = primaryButtonColors,
                ) {
                    Text("下一步")
                }
            }

            if (step >= 1) {
                Button(
                    onClick = onCreate,
                    enabled = canCreate && !isCreating,
                    modifier = Modifier.weight(1f),
                    shape = componentShapes.button,
                    colors = primaryButtonColors,
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("开始")
                    }
                }
            }
        }
    }
}

internal fun canMoveNext(state: NewSessionUiState): Boolean =
    when (state.step) {
        0 -> !state.provider.isNullOrBlank() && !state.hostId.isNullOrBlank()
        1 -> !state.cwd.isNullOrBlank()
        else -> false
    }

internal fun canCreate(state: NewSessionUiState): Boolean =
    !state.provider.isNullOrBlank() &&
        !state.hostId.isNullOrBlank() &&
        !state.cwd.isNullOrBlank()

private val STEP_TITLES = listOf("Provider", "目录", "消息")

private fun newSessionBannerHostId(state: NewSessionUiState): String? =
    if (state.provider == "claude" || state.provider == "book") {
        state.hostId
    } else {
        null
    }
