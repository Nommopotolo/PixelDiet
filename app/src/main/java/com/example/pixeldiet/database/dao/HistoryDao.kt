package com.example.pixeldiet.database.dao

import androidx.room.*
import com.example.pixeldiet.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    // --- Daily usage ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyUsage(usage: DailyUsageEntity)

    @Query("""
        SELECT * FROM daily_usage_history 
        WHERE uid = :uid 
          AND date BETWEEN :startDate AND :endDate 
        ORDER BY date ASC
    """)
    suspend fun getDailyUsages(uid: String, startDate: String, endDate: String): List<DailyUsageEntity>

    @Query("""
        SELECT * FROM daily_usage_history
        WHERE uid = :uid AND date = :date
        LIMIT 1
    """)
    suspend fun getDailyUsage(uid: String, date: String): DailyUsageEntity?

    @Query("""
        SELECT * FROM daily_usage_history
        WHERE uid = :uid
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getRecentDailyUsages(uid: String, limit: Int): List<DailyUsageEntity>

    // 🔹 Flow 기반 전체 조회 (자동 observe)
    @Query("SELECT * FROM daily_usage_history WHERE uid = :uid ORDER BY date ASC")
    fun observeDailyUsages(uid: String): Flow<List<DailyUsageEntity>>

    // --- Goal history ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalHistory(goal: GoalHistoryEntity)

    // ✅ 앱별 목표 조회
    @Query("""
        SELECT * FROM goal_history
        WHERE uid = :uid AND packageName = :packageName 
          AND effectiveDate <= :targetDate
        ORDER BY effectiveDate DESC 
        LIMIT 1
    """)
    suspend fun getEffectiveAppGoal(uid: String, packageName: String, targetDate: String): GoalHistoryEntity?

    // ✅ 전체 목표 조회
    @Query("""
        SELECT * FROM goal_history
        WHERE uid = :uid AND packageName IS NULL 
          AND effectiveDate <= :targetDate
        ORDER BY effectiveDate DESC 
        LIMIT 1
    """)
    suspend fun getEffectiveOverallGoal(uid: String, targetDate: String): GoalHistoryEntity?

    @Query("""
        SELECT * FROM goal_history
        WHERE uid = :uid 
          AND effectiveDate BETWEEN :startDate AND :endDate
        ORDER BY effectiveDate ASC
    """)
    suspend fun getGoalHistoryInRange(uid: String, startDate: String, endDate: String): List<GoalHistoryEntity>

    // 🔹 Flow 기반 전체 목표 observe
    @Query("SELECT * FROM goal_history WHERE uid = :uid AND packageName IS NULL ORDER BY effectiveDate DESC LIMIT 1")
    fun observeOverallGoal(uid: String): Flow<GoalHistoryEntity?>

    // --- Tracking history ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackingHistory(history: TrackingHistoryEntity)

    @Query("""
        SELECT * FROM tracking_history
        WHERE uid = :uid 
          AND effectiveDate <= :targetDate
        ORDER BY effectiveDate DESC 
        LIMIT 1
    """)
    suspend fun getEffectiveTrackingHistory(uid: String, targetDate: String): TrackingHistoryEntity?

    @Query("""
        SELECT * FROM tracking_history
        WHERE uid = :uid 
          AND effectiveDate BETWEEN :startDate AND :endDate
        ORDER BY effectiveDate ASC
    """)
    suspend fun getTrackingHistoryInRange(uid: String, startDate: String, endDate: String): List<TrackingHistoryEntity>

    // ✅ 가장 최근 기록 조회
    @Query("""
        SELECT * FROM tracking_history
        WHERE uid = :uid
        ORDER BY effectiveDate DESC
        LIMIT 1
    """)
    suspend fun getLatestTrackingHistory(uid: String): TrackingHistoryEntity?

    // 🔹 Flow 기반 최근 기록 observe
    @Query("SELECT * FROM tracking_history WHERE uid = :uid ORDER BY effectiveDate DESC LIMIT 1")
    fun observeLatestTrackingHistory(uid: String): Flow<TrackingHistoryEntity?>
}