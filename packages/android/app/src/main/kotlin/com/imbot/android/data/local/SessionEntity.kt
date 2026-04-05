package com.imbot.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val provider: String,
    @ColumnInfo(name = "host_id")
    val hostId: String,
    @ColumnInfo(name = "workspace_cwd")
    val workspaceCwd: String,
    @ColumnInfo(name = "initial_prompt")
    val initialPrompt: String?,
    val model: String?,
    val status: String,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    @ColumnInfo(name = "input_tokens")
    val inputTokens: Int = 0,
    @ColumnInfo(name = "output_tokens")
    val outputTokens: Int = 0,
    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "last_active_at")
    val lastActiveAt: String,
)
