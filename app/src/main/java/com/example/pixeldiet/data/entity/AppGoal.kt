package com.example.pixeldiet.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_goals")
data class AppGoal(
    @PrimaryKey val packageName: String,
    val dailyGoalMillis: Long
)
