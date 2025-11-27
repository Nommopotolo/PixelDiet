package com.example.pixeldiet.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.entity.AppGoal
import com.example.pixeldiet.model.AppName
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.NotificationSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object UsageRepository {

    private var prefs: NotificationPrefs? = null

    // Room DB & Firebase 인스턴스
    private val db by lazy { DatabaseProvider.getDatabase(ContextProvider.context) } // Context 처리를 위해 하단 ContextProvider 참고
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val _appUsageList = MutableLiveData<List<AppUsage>>()
    val appUsageList: LiveData<List<AppUsage>> = _appUsageList

    private val _dailyUsageList = MutableLiveData<List<DailyUsage>>()
    val dailyUsageList: LiveData<List<DailyUsage>> = _dailyUsageList

    private val _notificationSettings = MutableLiveData<NotificationSettings>()
    val notificationSettings: LiveData<NotificationSettings> = _notificationSettings

    private val currentGoals = mutableMapOf<AppName, Int>()

    // 초기화 블록
    init {
        val initialList = AppName.values().map { appName ->
            AppUsage(appName, 0, 0, 0)
        }
        _appUsageList.postValue(initialList)
    }

    private fun getPrefs(context: Context): NotificationPrefs {
        if (prefs == null) {
            prefs = NotificationPrefs(context.applicationContext)
            _notificationSettings.postValue(prefs!!.loadNotificationSettings())
        }
        return prefs!!
    }

    fun updateNotificationSettings(settings: NotificationSettings, context: Context) {
        getPrefs(context).saveNotificationSettings(settings)
        _notificationSettings.postValue(settings)
    }

    /**
     * [수정됨] 목표 시간 업데이트
     * 1. 메모리 업데이트
     * 2. Room DB 저장 (로컬 저장소 - 앱 꺼도 유지됨)
     * 3. Firebase 저장 (백업용)
     */
    suspend fun updateGoalTimes(goals: Map<AppName, Int>) {
        // 1. 메모리 업데이트
        currentGoals.clear()
        currentGoals.putAll(goals)

        val currentList = _appUsageList.value ?: emptyList()
        val newList = currentList.map {
            it.copy(goalTime = currentGoals[it.appName] ?: 0)
        }
        _appUsageList.postValue(newList)

        // 2. Room DB에 저장 (비동기 처리)
        withContext(Dispatchers.IO) {
            goals.forEach { (appName, minutes) ->
                // AppGoal 엔티티 생성 (DB에는 밀리초 단위로 저장한다고 가정하거나 분단위 그대로 저장)
                // 기존 코드의 Entity 정의에 따라 dailyGoalMillis라고 되어있으므로 변환 필요 여부 확인.
                // 여기서는 편의상 분 단위를 그대로 저장하거나, Entity 수정이 필요할 수 있습니다.
                // ADDGoal.kt를 보니 dailyGoalMillis(Long)입니다. 분 -> 밀리초 변환해서 저장
                val millis = minutes * 60 * 1000L
                val goalEntity = AppGoal(appName.packageName, millis)

                // Room에 삽입
                db.appGoalDao().insertGoal(goalEntity)
            }
        }

        // 3. Firebase에 백업 (로그인 상태인 경우)
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val goalData = goals.mapKeys { it.key.name } // Enum Key를 String으로 변환
            try {
                firestore.collection("users").document(uid)
                    .collection("goals").document("daily_goals")
                    .set(goalData, SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * [수정됨] 실제 데이터 로드
     * 1. Room DB에서 저장된 목표 시간 불러오기
     * 2. SystemService에서 사용 시간 불러오기
     */
    suspend fun loadRealData(context: Context) {
        // ContextProvider에 context 세팅 (init의 lazy 로딩을 위해)
        ContextProvider.context = context.applicationContext

        getPrefs(context)

        // --- 0. Room DB에서 목표 시간 가져오기 (데이터 유지 핵심) ---
        withContext(Dispatchers.IO) {
            val savedGoals = db.appGoalDao().getAllGoals()
            savedGoals.forEach { goalEntity ->
                // 패키지 명으로 AppName 찾기
                val appName = AppName.values().find { it.packageName == goalEntity.packageName }
                if (appName != null) {
                    // DB(밀리초) -> 앱(분) 변환
                    val minutes = (goalEntity.dailyGoalMillis / (1000 * 60)).toInt()
                    currentGoals[appName] = minutes
                }
            }
        }

        // --- 1. 오늘 사용량 계산 (기존 로직 유지) ---
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val todayStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val todayUsageMap = parseUsageStats(todayStats)

        // --- 2. 지난 30일 사용량 계산 (기존 로직 유지) ---
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val thirtyDaysAgo = calendar.timeInMillis
        val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, thirtyDaysAgo, endTime)

        // (기존 dailyStats 파싱 로직 복사)
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

        // --- 3. 스트릭 계산 ---
        val streakMap = calculateStreaks(newDailyList, currentGoals)

        // --- 4. 최종 리스트 생성 ---
        val newAppUsageList = AppName.values().map { appName ->
            val todayUsage = todayUsageMap[appName] ?: 0
            val goal = currentGoals[appName] ?: 0 // Room에서 불러온 목표 시간 적용
            val pastStreak = streakMap[appName] ?: 0

            var finalStreak = pastStreak
            // 오늘 목표 달성 여부에 따른 스트릭 가표시 (UI용)
            if (goal > 0 && todayUsage > goal) {
                if (pastStreak > 0) finalStreak = -1
                else if (pastStreak == 0) finalStreak = -1
                // 이미 실패 중이면 그대로 유지
            }

            AppUsage(
                appName = appName,
                currentUsage = todayUsage,
                goalTime = goal,
                streak = finalStreak
            )
        }
        _appUsageList.postValue(newAppUsageList)
    }

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

    private fun calculateStreaks(dailyList: List<DailyUsage>, goals: Map<AppName, Int>): Map<AppName, Int> {
        val streakMap = mutableMapOf<AppName, Int>()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

        // 오늘 날짜 제외하고 과거 데이터만 내림차순 정렬
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

            var streak = 0
            // 가장 최근 과거 데이터(어제)부터 연속 성공 확인
            for (day in pastDays) {
                val usage = day.appUsages[appName] ?: 0
                if (usage <= goal) {
                    streak++
                } else {
                    break // 실패한 날이 나오면 스트릭 중단
                }
            }
            streakMap[appName] = streak
        }
        return streakMap
    }
}

// 싱글톤 객체에서 Context 접근을 위한 헬퍼 (Context 주입 문제 해결용)
object ContextProvider {
    lateinit var context: Context
}