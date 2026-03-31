package com.imbot.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
