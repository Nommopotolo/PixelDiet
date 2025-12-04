package com.example.pixeldiet.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.pixeldiet.model.NotificationSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SharedPreferences를 관리하는 헬퍼 클래스
 * - 알림 설정 (켜기/끄기, 반복 시간)
 * - 마지막 알림 보낸 시간/날짜 기록
 */
class NotificationPrefs(context: Context, val uid: String) {


    // ✅ UID별 Prefs 파일로 분리
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PixelDietPrefs_$uid", Context.MODE_PRIVATE)

    // --- 오늘 날짜 (YYYY-MM-DD) ---
    private val todayString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

    // --- 알림 설정 저장/로드 ---
    fun saveNotificationSettings(settings: NotificationSettings) {
        prefs.edit().apply {
            putBoolean("ind_50", settings.individualApp50)
            putBoolean("ind_70", settings.individualApp70)
            putBoolean("ind_100", settings.individualApp100)
            putBoolean("total_50", settings.total50)
            putBoolean("total_70", settings.total70)
            putBoolean("total_100", settings.total100)
            putInt("repeat_interval", settings.repeatIntervalMinutes)
            apply()
        }
    }

    fun loadNotificationSettings(): NotificationSettings {
        return try {
            NotificationSettings(
                individualApp50 = prefs.getBoolean("ind_50", true),
                individualApp70 = prefs.getBoolean("ind_70", true),
                individualApp100 = prefs.getBoolean("ind_100", true),
                total50 = prefs.getBoolean("total_50", true),
                total70 = prefs.getBoolean("total_70", true),
                total100 = prefs.getBoolean("total_100", true),
                repeatIntervalMinutes = prefs.getInt("repeat_interval", 5)
            )
        } catch (e: ClassCastException) {
            // 🚨 잘못된 타입 저장된 경우 → Prefs 초기화
            e.printStackTrace()
            prefs.edit().clear().apply()
            NotificationSettings() // 기본값 반환
        }
    }

    fun hasSentToday(type: String): Boolean {
        return prefs.getString(type, null) == todayString
    }

    fun recordSentToday(type: String) {
        prefs.edit().putString(type, todayString).apply()
    }

    fun getLastRepeatSentTime(type: String): Long {
        return prefs.getLong(type, 0L)
    }

    fun recordRepeatSentTime(type: String) {
        prefs.edit().putLong(type, System.currentTimeMillis()).apply()
    }
}