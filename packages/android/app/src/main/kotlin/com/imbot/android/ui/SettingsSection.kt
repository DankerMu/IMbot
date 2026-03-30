@file:Suppress("FunctionName")

package com.imbot.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.imbot.android.R

@Composable
fun SettingsSection(
    relayUrl: String,
    token: String,
    onRelayUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_section_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = relayUrl,
            onValueChange = onRelayUrlChange,
            label = {
                Text(text = stringResource(R.string.relay_url_label))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = {
                Text(text = stringResource(R.string.token_label))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.save_settings))
        }
    }
}
