package com.imbot.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions ORDER BY last_active_at DESC, created_at DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        ORDER BY last_active_at DESC, created_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getPage(
        offset: Int,
        limit: Int,
    ): List<SessionEntity>

    @Query(
        """
        SELECT * FROM sessions
        WHERE workspace_cwd = :prefix OR workspace_cwd LIKE :escapedPrefix || '/%' ESCAPE '\'
        ORDER BY last_active_at DESC, created_at DESC
        """,
    )
    fun getByPathPrefix(
        prefix: String,
        escapedPrefix: String,
    ): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sessions WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

internal fun String.escapeSqlLikePattern(): String =
    replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
