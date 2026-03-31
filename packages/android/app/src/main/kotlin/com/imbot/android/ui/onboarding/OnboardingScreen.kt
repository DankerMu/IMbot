@file:Suppress("FunctionName")

package com.imbot.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenVisible by remember { mutableStateOf(false) }

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
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "IM",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "IMbot",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "连接你的 Relay 并开始使用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = uiState.relayUrl,
            onValueChange = viewModel::updateUrl,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Relay URL")
            },
            placeholder = {
                Text("https://")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.token,
            onValueChange = viewModel::updateToken,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Token")
            },
            singleLine = true,
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::testConnection,
            enabled = uiState.relayUrl.isNotBlank() && uiState.token.isNotBlank() && !uiState.isTesting,
            modifier = Modifier.fillMaxWidth(),
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

        if (uiState.testResult != null) {
            Spacer(modifier = Modifier.height(20.dp))
            TestResultPanel(testResult = uiState.testResult!!)
        }

        if (uiState.testResult is TestResult.Success) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = viewModel::saveAndProceed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始使用")
            }
        }
    }
}

@Composable
private fun TestResultPanel(testResult: TestResult) {
    val successColor = Color(0xFF2E7D32)
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.large,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (testResult) {
            is TestResult.Error -> {
                Text(
                    text = "✗ ${testResult.message}",
                    color = errorColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            is TestResult.Success -> {
                val macbookStatus = testResult.response.macbookHost()?.status ?: "offline"
                val openclawStatus = testResult.response.openClawHost()?.status ?: "offline"

                Text(
                    text = "✓ 连接成功！Relay v${testResult.response.version}",
                    color = successColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "MacBook: $macbookStatus",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "OpenClaw: $openclawStatus",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
