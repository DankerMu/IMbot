package com.imbot.android.ui.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.imbot.android.ui.theme.SuccessColor
import com.imbot.android.ui.theme.WarningColor

private val readToolNames = setOf("read", "readfile", "read_file")
private val writeToolNames = setOf("write", "edit", "multiedit", "notebookedit")
private val bashToolNames = setOf("bash", "execute", "shell", "command")
private val searchToolNames = setOf("grep", "glob", "websearch", "webfetch", "search")

internal enum class ToolCategory(
    val icon: ImageVector,
    val accentColor: @Composable () -> Color,
    val label: String,
) {
    READ(
        icon = Icons.Outlined.Description,
        accentColor = { MaterialTheme.colorScheme.primary },
        label = "读取文件",
    ),
    WRITE(
        icon = Icons.Outlined.Edit,
        accentColor = { WarningColor },
        label = "编辑文件",
    ),
    BASH(
        icon = Icons.Outlined.Terminal,
        accentColor = { SuccessColor },
        label = "执行命令",
    ),
    SEARCH(
        icon = Icons.Outlined.Search,
        accentColor = { MaterialTheme.colorScheme.primary },
        label = "搜索",
    ),
    OTHER(
        icon = Icons.Outlined.Build,
        accentColor = { MaterialTheme.colorScheme.onSurfaceVariant },
        label = "工具",
    ),
}

internal fun classifyTool(toolName: String): ToolCategory {
    val name = toolName.lowercase()
    return when {
        name in readToolNames -> ToolCategory.READ
        name in writeToolNames -> ToolCategory.WRITE
        name in bashToolNames -> ToolCategory.BASH
        name in searchToolNames -> ToolCategory.SEARCH
        else -> ToolCategory.OTHER
    }
}
