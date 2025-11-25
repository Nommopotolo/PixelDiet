package com.example.pixeldiet.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.pixeldiet.data.dao.AppGoalDao
import com.example.pixeldiet.data.dao.AppUsageDao
import com.example.pixeldiet.data.entity.AppUsageLog
import com.example.pixeldiet.data.entity.AppGoal

@Database(
    entities = [AppUsageLog::class, AppGoal::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun appGoalDao(): AppGoalDao
}
