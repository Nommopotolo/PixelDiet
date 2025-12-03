package com.example.pixeldiet.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pixeldiet.database.AppDatabase
import com.example.pixeldiet.database.entity.DailyUsageEntity
import com.example.pixeldiet.database.entity.GoalHistoryEntity
import com.example.pixeldiet.database.entity.TrackingHistoryEntity
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.NotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object UsageRepository {

    private var prefs: NotificationPrefs? = null

    private val _appUsageList = MutableLiveData<List<AppUsage>>()
    val appUsageList: LiveData<List<AppUsage>> = _appUsageList

    private val _dailyUsageList = MutableLiveData<List<DailyUsage>>()
    val dailyUsageList: LiveData<List<DailyUsage>> = _dailyUsageList

    private val _notificationSettings = MutableLiveData<NotificationSettings>()
    val notificationSettings: LiveData<NotificationSettings> = _notificationSettings

    private val currentGoals = mutableMapOf<String, Int>()

    init {
        _appUsageList.postValue(emptyList())
    }

    // ---------------- 알림 설정 ----------------

    private fun getPrefs(context: Context, uid: String): NotificationPrefs {
        if (prefs == null || prefs!!.uid != uid) {
            prefs = NotificationPrefs(context.applicationContext, uid)
            _notificationSettings.postValue(prefs!!.loadNotificationSettings())
        }
        return prefs!!
    }

    fun updateNotificationSettings(settings: NotificationSettings, context: Context) {
        val uid = getUid(context)
        getPrefs(context, uid).saveNotificationSettings(settings)
        _notificationSettings.postValue(settings)
    }


    // ---------------- 목표 시간 업데이트 ----------------
    suspend fun updateGoalTimes(goals: Map<String, Int>, context: Context) {
        currentGoals.clear()
        currentGoals.putAll(goals)

        val uid = getUid(context)
        val today = todayString()
        val dao = AppDatabase.getInstance(context).historyDao()

        // DB에 GoalHistory 기록
        withContext(Dispatchers.IO) {
            goals.forEach { (pkg, minutes) ->
                dao.insertGoalHistory(
                    GoalHistoryEntity(
                        uid = uid,
                        effectiveDate = today,
                        packageName = pkg,
                        goalMinutes = minutes
                    )
                )
            }
        }

        val currentList = _appUsageList.value ?: emptyList()
        val newList = currentList.map { usage ->
            usage.copy(goalTime = currentGoals[usage.packageName] ?: 0)
        }
        _appUsageList.postValue(newList)
    }

    // ---------------- 실제 데이터 로딩 ----------------
    suspend fun loadRealData(context: Context) {
        val uid = getUid(context)
        val prefs = getPrefs(context, uid)
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        intent.addCategory(android.content.Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackage = resolveInfo?.activityInfo?.packageName

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val myPackage = context.packageName
        val preciseUsageMap = calculatePreciseUsage(context, startTime, endTime)

        val todayUsageMap: Map<String, Int> =
            preciseUsageMap.filterKeys { it != myPackage && it != launcherPackage }
                .filterValues { it > 0 }

        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val thirtyDaysAgo = calendar.timeInMillis
        val dailyStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            thirtyDaysAgo,
            endTime
        )

        val dailyUsageMap = mutableMapOf<String, MutableMap<String, Int>>()
        val todayKey = sdf.format(Date())   // ✅ KST 현재 날짜로 고정

        for (stat in dailyStats) {
            val usageInMinutes = (stat.totalTimeInForeground / (1000 * 60)).toInt()
            if (usageInMinutes <= 0) continue
            val pkg = stat.packageName

            // ✅ 모든 기록을 KST 오늘 날짜(todayKey)에 묶음
            val dayMap = dailyUsageMap.getOrPut(todayKey) { mutableMapOf() }
            dayMap[pkg] = (dayMap[pkg] ?: 0) + usageInMinutes
        }

        if (todayUsageMap.isNotEmpty()) {
            val todayDayMap = dailyUsageMap.getOrPut(todayKey) { mutableMapOf() }
            todayUsageMap.forEach { (pkg, minutes) -> todayDayMap[pkg] = minutes }
        }

        val dao = AppDatabase.getInstance(context).historyDao()

        // DB에 오늘 DailyUsage 기록
        withContext(Dispatchers.IO) {
            dao.upsertDailyUsage(
                DailyUsageEntity(
                    uid = uid,
                    date = todayKey,
                    appUsages = todayUsageMap
                )
            )
        }

        // DB에서 최근 30일 조회
        val newDailyList = withContext(Dispatchers.IO) {
            dao.getDailyUsages(uid, sdf.format(Date(thirtyDaysAgo)), todayKey)
                .map { DailyUsage(it.date, it.appUsages) }
        }
        _dailyUsageList.postValue(newDailyList)

        val streakMap = calculateStreaks(newDailyList, currentGoals)

        // DB에서 오늘 기준 추적앱 조회
// DB에서 최근 기록 기준 추적앱 조회
        val trackedPackages = withContext(Dispatchers.IO) {
            dao.getLatestTrackingHistory(uid)?.trackedPackages?.toSet() ?: emptySet()
        }

        val packageNames = mutableSetOf<String>()
        packageNames.addAll(trackedPackages)
        packageNames.addAll(todayUsageMap.keys)
        packageNames.addAll(currentGoals.keys)
        packageNames.addAll(streakMap.keys)

        val todayStr = todayString()

        val newAppUsageList = packageNames.map { pkg ->
            val todayUsage = todayUsageMap[pkg] ?: 0

            // ✅ DB에서 앱별 목표 조회
            val appGoal = withContext(Dispatchers.IO) {
                dao.getEffectiveAppGoal(uid, pkg, todayStr)?.goalMinutes
            }

            val goal = appGoal ?: currentGoals[pkg] ?: 0
            val pastStreak = streakMap[pkg] ?: 0
            val finalStreak = if (goal <= 0) 0 else {
                val todaySuccess = todayUsage <= goal
                when {
                    pastStreak == 0 -> if (todaySuccess) 1 else -1
                    pastStreak > 0 -> if (todaySuccess) pastStreak + 1 else -1
                    else -> if (todaySuccess) 1 else pastStreak - 1
                }
            }

            val label = try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg
            }

            val icon: Drawable? = try {
                packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }

            AppUsage(
                packageName = pkg,
                appLabel = label,
                icon = icon,
                currentUsage = todayUsage,
                goalTime = goal,   // ✅ DB 값 우선 반영
                streak = finalStreak
            )
        }.sortedBy { it.appLabel.lowercase() }

        _appUsageList.postValue(newAppUsageList)
    }

    // ---------------- 보조 함수들 ----------------
    private fun parseUsageStats(stats: List<UsageStats>): Map<String, Int> {
        val usageMap = mutableMapOf<String, Int>()
        for (stat in stats) {
            val usageInMinutes = (stat.totalTimeInForeground / (1000 * 60)).toInt()
            if (usageInMinutes <= 0) continue
            val pkg = stat.packageName
            usageMap[pkg] = (usageMap[pkg] ?: 0) + usageInMinutes
        }
        return usageMap
    }

    private fun calculateStreaks(
        dailyList: List<DailyUsage>,
        goals: Map<String, Int>
    ): Map<String, Int> {
        val streakMap = mutableMapOf<String, Int>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val todayStr = sdf.format(Date())

        val pastDays = dailyList.filter { it.date != todayStr }.sortedByDescending { it.date }
        if (pastDays.isEmpty()) {
            goals.keys.forEach { streakMap[it] = 0 }
            return streakMap
        }

        for ((pkg, goal) in goals) {
            if (goal == 0) {
                streakMap[pkg] = 0
                continue
            }
            val firstDayUsage = pastDays.first().appUsages[pkg] ?: 0
            val wasSuccess = firstDayUsage <= goal
            var streak = 0

            for (day in pastDays) {
                val usage = day.appUsages[pkg] ?: 0
                if ((usage <= goal) == wasSuccess) {
                    streak++
                } else {
                    break
                }
            }

            streakMap[pkg] = if (wasSuccess) streak else -streak
        }

        return streakMap
    }

    // ---------------- 정밀 시간 계산 함수 ----------------
    private fun calculatePreciseUsage(context: Context, startTime: Long, endTime: Long): Map<String, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

        val appUsageMap = mutableMapOf<String, Long>()
        val startMap = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    startMap[pkg] = event.timeStamp
                }

                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    startMap[pkg]?.let { sTime ->
                        val duration = event.timeStamp - sTime
                        if (duration > 0) {
                            appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
                        }
                        startMap.remove(pkg)
                    }
                }

                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    startMap.forEach { (p, sTime) ->
                        val duration = event.timeStamp - sTime
                        if (duration > 0) {
                            appUsageMap[p] = (appUsageMap[p] ?: 0L) + duration
                        }
                    }
                    startMap.clear()
                }
            }
        }

        // 마지막까지 켜져 있는 앱 처리
        startMap.forEach { (pkg, sTime) ->
            val duration = endTime - sTime
            if (duration > 0) {
                appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
            }
        }

        return appUsageMap.mapValues { (_, millis) -> (millis / (1000 * 60)).toInt() }
    }

    // ---------------- 유틸 함수 ----------------
    private fun getUid(context: Context): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.uid ?: "anonymous"
    }


    private fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        return sdf.format(Date())
    }

    // ✅ UID 변경 이벤트 감지
    fun attachAuthListener(context: Context) {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid ?: "anonymous"

            prefs = NotificationPrefs(context.applicationContext, uid)
            _notificationSettings.postValue(prefs!!.loadNotificationSettings())

            _appUsageList.postValue(emptyList())
            _dailyUsageList.postValue(emptyList())
            currentGoals.clear()

            if (uid != "anonymous") {
                CoroutineScope(Dispatchers.IO).launch {
                    loadRealData(context)
                }
            }
        }
    }

}