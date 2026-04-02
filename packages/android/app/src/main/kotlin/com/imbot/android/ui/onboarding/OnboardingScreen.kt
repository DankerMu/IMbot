@file:Suppress("FunctionName")

package com.imbot.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.SuccessColor
import com.imbot.android.ui.theme.SurfaceTertiary
import com.imbot.android.ui.theme.SurfaceTertiaryDark
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import com.imbot.android.ui.theme.imbotFilledTextFieldColors
import com.imbot.android.ui.theme.spacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenVisible by remember { mutableStateOf(false) }
    val componentShapes = LocalIMbotComponentShapes.current
    val spacing = MaterialTheme.spacing
    val isDarkTheme = LocalUseDarkTheme.current
    val buttonColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (isDarkTheme) SurfaceTertiaryDark else SurfaceTertiary,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        )

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            if (event is OnboardingEvent.NavigateHome) {
                onNavigateHome()
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(92.dp)
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                ),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "IM",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "IMbot",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "连接你的 Relay 并开始使用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextField(
                value = uiState.relayUrl,
                onValueChange = viewModel::updateUrl,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Relay URL", style = MaterialTheme.typography.labelLarge)
                },
                placeholder = {
                    Text("https://")
                },
                singleLine = true,
                shape = componentShapes.input,
                colors = imbotFilledTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            TextField(
                value = uiState.token,
                onValueChange = viewModel::updateToken,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Token", style = MaterialTheme.typography.labelLarge)
                },
                singleLine = true,
                shape = componentShapes.input,
                colors = imbotFilledTextFieldColors(),
                textStyle = MaterialTheme.typography.bodyLarge,
                visualTransformation =
                    if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            tokenVisible = !tokenVisible
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (tokenVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                            contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                        )
                    }
                },
            )

            Button(
                onClick = viewModel::testConnection,
                enabled = uiState.relayUrl.isNotBlank() && uiState.token.isNotBlank() && !uiState.isTesting,
                modifier = Modifier.fillMaxWidth(),
                shape = componentShapes.button,
                colors = buttonColors,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("测试连接")
                }
            }

            uiState.testResult?.let { testResult ->
                TestResultPanel(testResult = testResult)
            }

            if (uiState.testResult is TestResult.Success) {
                Button(
                    onClick = viewModel::saveAndProceed,
                    modifier = Modifier.fillMaxWidth(),
                    shape = componentShapes.button,
                    colors = buttonColors,
                ) {
                    Text("开始使用")
                }
            }
        }
    }
}

@Composable
private fun TestResultPanel(testResult: TestResult) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow
    val successColor = SuccessColor
    val errorColor = MaterialTheme.colorScheme.error

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .appleChrome(
                    shape = componentShapes.card,
                    isDarkTheme = isDarkTheme,
                    outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                    shadowTokens = shadowTokens,
                ),
        color = MaterialTheme.colorScheme.surface,
        shape = componentShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (testResult) {
                is TestResult.Error -> {
                    Text(
                        text = "✕ ${testResult.message}",
                        color = errorColor,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                is TestResult.Success -> {
                    val macbookStatus = testResult.response.macbookHost()?.status ?: "offline"
                    val openclawStatus = testResult.response.openClawHost()?.status ?: "offline"

                    Text(
                        text = "✓ 连接成功！Relay v${testResult.response.version}",
                        color = successColor,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "MacBook: $macbookStatus",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "OpenClaw: $openclawStatus",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
