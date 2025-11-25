package com.example.pixeldiet.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pixeldiet.model.AppName
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.NotificationSettings
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object UsageRepository {

    // ⭐️ SharedPreferences 인스턴스를 저장할 변수
    private var prefs: NotificationPrefs? = null

    private val _appUsageList = MutableLiveData<List<AppUsage>>()
    val appUsageList: LiveData<List<AppUsage>> = _appUsageList

    private val _dailyUsageList = MutableLiveData<List<DailyUsage>>()
    val dailyUsageList: LiveData<List<DailyUsage>> = _dailyUsageList

    private val _notificationSettings = MutableLiveData<NotificationSettings>()
    val notificationSettings: LiveData<NotificationSettings> = _notificationSettings

    private val currentGoals = mutableMapOf<AppName, Int>()

    // ⭐️ [수정] init 블록: 0값 초기화
    init {
        val initialList = AppName.values().map { appName ->
            AppUsage(appName, 0, 0, 0) // icon이 없는 4개 파라미터
        }
        _appUsageList.postValue(initialList)
    }

    // ⭐️ [신규] SharedPreferences를 초기화하는 함수
    // (Context가 필요한 시점에 ViewModel이 호출)
    private fun getPrefs(context: Context): NotificationPrefs {
        if (prefs == null) {
            prefs = NotificationPrefs(context.applicationContext)
            // ⭐️ 초기화 시, SharedPreferences에서 설정을 불러와 LiveData에 반영
            _notificationSettings.postValue(prefs!!.loadNotificationSettings())
        }
        return prefs!!
    }

    // ⭐️ [수정] SharedPreferences에 저장하도록 변경
    fun updateNotificationSettings(settings: NotificationSettings, context: Context) { // ⭐️ Context 추가
        // 1. SharedPreferences에 영구 저장
        getPrefs(context).saveNotificationSettings(settings) // ⭐️ 전달받은 Context 사용
        // 2. LiveData에 반영 (UI 즉시 업데이트용)
        _notificationSettings.postValue(settings)
    }

    fun updateGoalTimes(goals: Map<AppName, Int>) {
        currentGoals.clear()
        currentGoals.putAll(goals)
        val currentList = _appUsageList.value!!
        val newList = currentList.map {
            it.copy(goalTime = currentGoals[it.appName] ?: 0)
        }
        _appUsageList.postValue(newList)
    }

    suspend fun loadRealData(context: Context) {
        // ⭐️ [수정] loadRealData가 호출될 때 prefs가 초기화되도록 보장
        val prefs = getPrefs(context)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val calendar = Calendar.getInstance()

        // --- 1. 오늘 사용량 계산 ---
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        val todayStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val todayUsageMap = parseUsageStats(todayStats)

        // --- 2. 지난 30일 사용량 계산 ---
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val thirtyDaysAgo = calendar.timeInMillis
        val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, thirtyDaysAgo, endTime)
        val dailyUsageMap = mutableMapOf<String, MutableMap<AppName, Int>>()
        val appPackages = AppName.values().map { it.packageName }.toSet()

        for (stat in dailyStats) {
            if (stat.packageName in appPackages) {
                val appName = AppName.values().find { it.packageName == stat.packageName }!!
                val date = sdf.format(Date(stat.firstTimeStamp))
                val usageInMinutes = (stat.totalTimeInForeground / (1000 * 60)).toInt()
                val dayMap = dailyUsageMap.getOrPut(date) { mutableMapOf() }
                dayMap[appName] = (dayMap[appName] ?: 0) + usageInMinutes
            }
        }
        val newDailyList = dailyUsageMap.map { (date, usages) ->
            DailyUsage(
                date = date,
                appUsages = mapOf(
                    AppName.NAVER_WEBTOON to (usages[AppName.NAVER_WEBTOON] ?: 0),
                    AppName.INSTAGRAM to (usages[AppName.INSTAGRAM] ?: 0),
                    AppName.YOUTUBE to (usages[AppName.YOUTUBE] ?: 0)
                )
            )
        }.sortedBy { it.date }
        _dailyUsageList.postValue(newDailyList)

        // --- 3. 어제까지의 스트릭 계산 ---
        val streakMap = calculateStreaks(newDailyList, currentGoals)

        // --- 4. 최종 _appUsageList 생성 (아이콘 로직 없음) ---
        val newAppUsageList = AppName.values().map { appName ->
            val todayUsage = todayUsageMap[appName] ?: 0
            val goal = currentGoals[appName] ?: 0
            val pastStreak = streakMap[appName] ?: 0
            var finalStreak = pastStreak

            if (goal > 0 && todayUsage > goal) {
                if (pastStreak < 0) {
                    finalStreak = pastStreak - 1
                } else {
                    finalStreak = -1
                }
            }

            AppUsage(
                appName = appName,
                currentUsage = todayUsage,
                goalTime = goal,
                streak = finalStreak
                // ⭐️ icon 필드 없음
            )
        }
        _appUsageList.postValue(newAppUsageList)
    }

    // ... (parseUsageStats, calculateStreaks 함수는 이전과 동일) ...
    private fun parseUsageStats(stats: List<UsageStats>): Map<AppName, Int> {
        val usageMap = mutableMapOf<AppName, Int>()
        val appPackages = AppName.values().map { it.packageName }.toSet()
        for (stat in stats) {
            if (stat.packageName in appPackages) {
                val appName = AppName.values().find { it.packageName == stat.packageName }!!
                val usageInMinutes = (stat.totalTimeInForeground / (1000 * 60)).toInt()
                usageMap[appName] = (usageMap[appName] ?: 0) + usageInMinutes
            }
        }
        return usageMap
    }

    private fun calculateStreaks(
        dailyList: List<DailyUsage>,
        goals: Map<AppName, Int>
    ): Map<AppName, Int> {

        val streakMap = mutableMapOf<AppName, Int>()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val pastDays = dailyList.filter { it.date != todayStr }.sortedByDescending { it.date }

        if (pastDays.isEmpty()) {
            AppName.values().forEach { streakMap[it] = 0 }
            return streakMap
        }

        for (appName in AppName.values()) {
            val goal = goals[appName] ?: 0
            if (goal == 0) {
                streakMap[appName] = 0
                continue
            }
            val firstDayUsage = pastDays.first().appUsages[appName] ?: 0
            val wasSuccess = firstDayUsage <= goal
            var streak = 0
            for (day in pastDays) {
                val usage = day.appUsages[appName] ?: 0
                if ((usage <= goal) == wasSuccess) {
                    streak++
                } else {
                    break
                }
            }
            streakMap[appName] = if (wasSuccess) streak else -streak
        }
        return streakMap
    }
}