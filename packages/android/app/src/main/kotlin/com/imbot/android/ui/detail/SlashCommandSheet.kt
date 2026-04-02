@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlashCommandSheet(
    onDismiss: () -> Unit,
    onSkillSelected: (SkillItem) -> Unit,
    modifier: Modifier = Modifier,
    skills: List<SkillItem> = DEFAULT_SKILLS,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredSkills = filterSkills(query, skills)
    val groupedSkills = filteredSkills.groupBy(SkillItem::category)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "选择命令",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("搜索 slash command")
                },
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SkillCategory.entries.forEach { category ->
                    val itemsInCategory = groupedSkills[category].orEmpty()
                    if (itemsInCategory.isEmpty()) {
                        return@forEach
                    }

                    item(key = "header-${category.name}") {
                        Text(
                            text = category.label(),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(
                        items = itemsInCategory,
                        key = SkillItem::command,
                    ) { skill ->
                        SkillRow(
                            skill = skill,
                            onClick = {
                                onSkillSelected(skill)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillItem,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "/ ${skill.command}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = skill.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private fun SkillCategory.label(): String =
    when (this) {
        SkillCategory.BUILT_IN -> "Built-in"
        SkillCategory.AGENT_SKILL -> "Agent Skills"
        SkillCategory.SLASH_COMMAND -> "Slash Commands"
    }
