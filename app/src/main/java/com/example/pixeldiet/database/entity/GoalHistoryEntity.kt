package com.example.pixeldiet.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_history",
    indices = [
        Index(value = ["uid", "effectiveDate", "packageName"], unique = false) // uid + 날짜 + 앱 기준 조회 최적화
    ]
)
data class GoalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,                // 사용자 UID (Firebase UID 또는 "anonymous")
    val effectiveDate: String,      // "YYYY-MM-DD" 형식
    val packageName: String?,       // null = 전체 목표, 값 있으면 특정 앱 목표
    val goalMinutes: Int            // 목표 시간 (분 단위)
)