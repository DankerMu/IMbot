package com.imbot.android.data.repository

import com.imbot.android.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow

interface SessionStore {
    suspend fun clearLocalCache()

    fun getSessionsByPathPrefix(pathPrefix: String): Flow<List<SessionEntity>>
}
