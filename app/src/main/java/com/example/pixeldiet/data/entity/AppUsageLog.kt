package com.example.pixeldiet.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage_logs")
data class AppUsageLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val packageName: String,
    val usageTimeMillis: Long
)
