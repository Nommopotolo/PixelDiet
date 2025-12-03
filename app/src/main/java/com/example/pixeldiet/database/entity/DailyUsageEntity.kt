package com.example.pixeldiet.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_usage_history",
    indices = [
        Index(value = ["uid", "date"], unique = true) // uid + date 조합은 유일해야 함
    ]
)
data class DailyUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,                    // 사용자 UID (Firebase UID 또는 "anonymous")
    val date: String,                   // "YYYY-MM-DD" 형식
    val appUsages: Map<String, Int>     // 앱별 사용 시간 (분 단위, Converters 필요)
)