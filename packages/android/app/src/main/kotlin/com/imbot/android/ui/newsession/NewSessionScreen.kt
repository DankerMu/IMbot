@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                title = {
                    Text("新建会话")
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
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
                    .padding(innerPadding),
        ) {
            ErrorBannerHost(
                errorState = errorState,
                scope = ErrorScope.WORKSPACE,
                modifier = Modifier.padding(horizontal = 16.dp),
                hostId = newSessionBannerHostId(uiState),
            )
            StepIndicator(
                currentStep = uiState.step,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            )

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

@Composable
private fun StepIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            STEP_TITLES.forEachIndexed { index, _ ->
                StepDot(
                    active = index <= currentStep,
                )
                if (index < STEP_TITLES.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    color =
                                        if (index < currentStep) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            STEP_TITLES.forEachIndexed { index, title ->
                Text(
                    text = title,
                    color =
                        if (index == currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StepDot(active: Boolean) {
    Box(
        modifier =
            Modifier
                .size(12.dp)
                .border(
                    width = 1.dp,
                    color =
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    shape = CircleShape,
                )
                .background(
                    color =
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    shape = CircleShape,
                ),
    )
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

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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

private val STEP_TITLES = listOf("Provider", "目录", "消息（可选）")

private fun newSessionBannerHostId(state: NewSessionUiState): String? =
    if (state.provider == "claude" || state.provider == "book") {
        state.hostId
    } else {
        null
    }
