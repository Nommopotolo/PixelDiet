package com.example.pixeldiet.data.dao

import androidx.room.*
import com.example.pixeldiet.data.entity.AppGoal
import com.example.pixeldiet.data.entity.AppUsageLog

@Dao
interface AppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(log: AppUsageLog)

    @Query("SELECT * FROM app_usage_logs WHERE date = :date")
    suspend fun getUsageByDate(date: String): List<AppUsageLog>
}

@Dao
interface AppGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: AppGoal)

    @Query("SELECT * FROM app_goals")
    suspend fun getAllGoals(): List<AppGoal>
}
