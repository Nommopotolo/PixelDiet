package com.example.pixeldiet.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration   // ✅ Migration 클래스 import
import com.example.pixeldiet.database.dao.HistoryDao
import com.example.pixeldiet.database.dao.SocialDao   // ✅ 새 DAO import
import com.example.pixeldiet.database.entity.*

@Database(
    entities = [
        DailyUsageEntity::class,
        GoalHistoryEntity::class,
        TrackingHistoryEntity::class,
        UserProfileEntity::class,   // ✅ 사용자 프로필
        FriendEntity::class,        // ✅ 친구 관계
        GroupEntity::class,         // ✅ 그룹 정보
        GroupMemberEntity::class    // ✅ 그룹 멤버
    ],
    version = 3,   // ✅ 새 엔티티 추가했으므로 버전 올림
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun socialDao(): SocialDao   // ✅ 새 DAO 등록

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // ✅ 기존 Migration (1 → 2)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 컬럼 추가
                database.execSQL("ALTER TABLE goal_history ADD COLUMN uid TEXT NOT NULL DEFAULT 'anonymous'")
                database.execSQL("ALTER TABLE tracking_history ADD COLUMN uid TEXT NOT NULL DEFAULT 'anonymous'")
                database.execSQL("ALTER TABLE daily_usage_history ADD COLUMN uid TEXT NOT NULL DEFAULT 'anonymous'")

                // 인덱스 추가
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_uid_date_pkg ON goal_history(uid, effectiveDate, packageName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_track_uid_date ON tracking_history(uid, effectiveDate)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_uid_date ON daily_usage_history(uid, date)")
            }
        }

        // ✅ 새 Migration (2 → 3): 친구/그룹 테이블 생성
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // user_profile 테이블
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_profile (
                        uid TEXT PRIMARY KEY NOT NULL,
                        nickname TEXT NOT NULL,
                        profileImageUri TEXT
                    )
                """)

                // friends 테이블
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS friends (
                        ownerUid TEXT NOT NULL,
                        friendUid TEXT NOT NULL,
                        sinceDate TEXT NOT NULL,
                        PRIMARY KEY(ownerUid, friendUid)
                    )
                """)

                // groups 테이블
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS groups (
                        groupId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupName TEXT NOT NULL,
                        goalMinutes INTEGER NOT NULL,
                        createdDate TEXT NOT NULL
                    )
                """)

                // group_members 테이블
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        groupId INTEGER NOT NULL,
                        memberUid TEXT NOT NULL,
                        joinDate TEXT NOT NULL,
                        successCount INTEGER NOT NULL DEFAULT 0,
                        failCount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(groupId, memberUid)
                    )
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixeldiet_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)   // ✅ 모든 Migration 적용
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}