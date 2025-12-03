package com.example.pixeldiet.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_history",
    indices = [
        Index(value = ["uid", "effectiveDate"], unique = false) // uid + 날짜 기준 조회 최적화
    ]
)
data class TrackingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,                 // 사용자 UID (Firebase UID 또는 "anonymous")
    val effectiveDate: String,       // "YYYY-MM-DD" 형식
    val trackedPackages: List<String> // 추적 앱 목록 (Converters 필요)
)