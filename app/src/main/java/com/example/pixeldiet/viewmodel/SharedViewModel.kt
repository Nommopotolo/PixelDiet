package com.example.pixeldiet.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.CalendarDecoratorData
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.DayStatus
import com.example.pixeldiet.model.NotificationSettings
import com.example.pixeldiet.repository.UsageRepository
import com.github.mikephil.charting.data.Entry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import com.example.pixeldiet.database.AppDatabase
import com.example.pixeldiet.database.entity.TrackingHistoryEntity
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import com.example.pixeldiet.database.entity.GoalHistoryEntity
import com.example.pixeldiet.backup.BackupManager
class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository
    val appUsageList: LiveData<List<AppUsage>> = repository.appUsageList
    private val dailyUsageList: LiveData<List<DailyUsage>> = repository.dailyUsageList
    val notificationSettings: LiveData<NotificationSettings> = repository.notificationSettings

    // ❗ AppName 대신 packageName(String?)으로 필터 (null = 전체)
    private val _selectedFilter = MutableLiveData<String?>(null)

    // 🔹 캘린더/그래프에서 쓸 현재 필터 라벨("전체" / 앱 이름)
    val selectedFilterText: LiveData<String> = MediatorLiveData<String>().apply {

        fun update() {
            val pkg = _selectedFilter.value
            val list = appUsageList.value ?: emptyList()

            value = if (pkg == null) {
                // 전체 보기
                "전체"
            } else {
                // packageName에 해당하는 앱 찾기 → appLabel 사용
                list.find { it.packageName == pkg }?.appLabel ?: "전체"
            }
        }

        addSource(_selectedFilter) { update() }
        addSource(appUsageList) { update() }   // 앱 리스트가 바뀌어도 라벨 갱신
    }

    // ----------------------- 추적 앱 목록 -----------------------

    private fun getTrackedPrefs(uid: String): SharedPreferences {
        return getApplication<Application>()
            .getSharedPreferences("tracked_apps_prefs_$uid", Context.MODE_PRIVATE)
    }

    private fun getGoalPrefs(uid: String): SharedPreferences {
        return getApplication<Application>()
            .getSharedPreferences("goal_prefs_$uid", Context.MODE_PRIVATE)
    }
    // 현재 추적 중인 앱들의 packageName 집합
    private val _trackedPackages = MutableLiveData<Set<String>>(emptySet())
    val trackedPackages: LiveData<Set<String>> = _trackedPackages

    // 🔹 전체 목표시간 (분). null 이면 "앱별 목표 합산" 사용
    private val _overallGoalMinutes = MutableLiveData<Int?>(null)
    val overallGoalMinutes: LiveData<Int?> = _overallGoalMinutes

    private fun loadTrackedPackages() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(getApplication()).historyDao()
            val tracked = if (uid != "anonymous") {
                // ✅ 계정 UID일 때는 Prefs 무시, DB만 사용
                dao.getLatestTrackingHistory(uid)?.trackedPackages?.toSet() ?: emptySet()
            } else {
                // ✅ 게스트일 때만 Prefs 사용
                getTrackedPrefs(uid).getStringSet("tracked_packages", emptySet()) ?: emptySet()
            }
            _trackedPackages.postValue(tracked)
        }
    }

    fun updateTrackedPackages(newSet: Set<String>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        _trackedPackages.value = newSet
        getTrackedPrefs(uid).edit().putStringSet("tracked_packages", newSet).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val today = todayString()
            val dao = AppDatabase.getInstance(getApplication()).historyDao()

            val entity = TrackingHistoryEntity(
                uid = uid,
                effectiveDate = today,
                trackedPackages = newSet.toList()
            )

            // ✅ Room 저장
            dao.insertTrackingHistory(entity)

            // ✅ Firestore 업데이트: 빈 값일 때는 무시
            if (newSet.isNotEmpty()) {
                BackupManager().backupTrackingHistory(uid, entity)
            } else {
                Log.d("SharedViewModel", "빈 trackedPackages → Firestore 백업 건너뜀")
            }

            // ✅ Firestore 업데이트 성공 시에만 데이터 로드
            if (newSet.isNotEmpty()) {
                repository.loadRealData(getApplication())
            }
        }
    }



    private fun loadOverallGoal() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(getApplication()).historyDao()
            val goal = if (uid != "anonymous") {
                // ✅ 계정 UID일 때는 DB에서 불러오기
                dao.getEffectiveOverallGoal(uid, todayString())?.goalMinutes
            } else {
                // ✅ 게스트일 때만 Prefs 사용
                val saved = getGoalPrefs(uid).getInt("overall_goal_minutes", -1)
                if (saved >= 0) saved else null
            }
            _overallGoalMinutes.postValue(goal)
        }
    }



    fun setOverallGoal(minutes: Int?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        _overallGoalMinutes.value = minutes

        if (uid == "anonymous") {
            getGoalPrefs(uid).edit().apply {
                if (minutes == null) {
                    remove("overall_goal_minutes")
                } else {
                    putInt("overall_goal_minutes", minutes)
                }
            }.apply()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(getApplication()).historyDao()

                val entity = GoalHistoryEntity(
                    uid = uid,
                    effectiveDate = todayString(),
                    packageName = null,          // 전체 목표이므로 null
                    goalMinutes = minutes ?: 0
                )

                // ✅ Room 저장
                dao.insertGoalHistory(entity)

                // ✅ Firestore 백업 추가
                BackupManager().backupGoalHistory(uid, entity)

                loadOverallGoal()
            }
        }
    }

    fun backupTodayUsage() = viewModelScope.launch(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val dao = AppDatabase.getInstance(getApplication()).historyDao()

        val today = todayString()

        // 🔹 현재 앱 사용 데이터 가져오기
        val usages = appUsageList.value ?: emptyList()

        // 🔹 추적 앱 목록 가져오기
        val tracked = trackedPackages.value ?: emptySet()

        // 🔹 오늘 날짜의 실제 사용 시간 Map 생성
        val appUsages: Map<String, Int> = usages
            .filter { tracked.isEmpty() || it.packageName in tracked }   // ✅ 추적 앱만 반영
            .associate { it.packageName to it.currentUsage }             // ✅ packageName → 사용시간(분)

        val entity = com.example.pixeldiet.database.entity.DailyUsageEntity(
            uid = uid,
            date = today,
            appUsages = appUsages       // ✅ 실제 사용 시간 반영
        )

        // ✅ Room 저장
        dao.upsertDailyUsage(entity)

        // ✅ Firestore 백업
        if (uid != "anonymous") {
            BackupManager().backupDailyUsage(uid, entity)
        }
    }

    // ----------------------- Firebase Auth -----------------------

    private val auth = FirebaseAuth.getInstance()
    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName

    val isGoogleUser = MutableStateFlow(isGoogleLogin())
    private val authListener = FirebaseAuth.AuthStateListener { _ ->
        _userName.value = getUserName()
        isGoogleUser.value = isGoogleLogin()
    }

    init {
        Log.d("SharedViewModel", "앱 시작됨 (SharedViewModel init)")   // 시작 로그
        auth.addAuthStateListener(authListener)  // 람다 대신 authListener 등록
        val uid = auth.currentUser?.uid ?: "anonymous"

        // ✅ UI 상태도 갱신
            _userName.value = getUserName()
            isGoogleUser.value = isGoogleLogin()

            viewModelScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(getApplication()).historyDao()

                val tracked = if (uid != "anonymous") {
                    dao.getLatestTrackingHistory(uid)?.trackedPackages?.toSet() ?: emptySet()
                } else {
                    getTrackedPrefs(uid).getStringSet("tracked_packages", emptySet()) ?: emptySet()
                }
                _trackedPackages.postValue(tracked)

                // ✅ 목표 시간도 DB 기반으로 불러오기
                loadOverallGoal()

                if (uid != "anonymous") {
                    repository.loadRealData(getApplication())
                }
            }
        }

    override fun onCleared() {
        super.onCleared()
        Log.d("SharedViewModel", "앱 종료됨 (SharedViewModel onCleared)")   // 종료 로그
        auth.removeAuthStateListener(authListener) // 동일 객체 제거
    }

    fun onGoogleLoginSuccess(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()

                val uid = auth.currentUser?.uid ?: return@launch
                Log.d("SharedViewModel", "구글 로그인 성공: $uid")

                // ✅ Firestore → Room 복원
                val context = getApplication<Application>().applicationContext
                val backupManager = BackupManager()

                val dailyRestored = backupManager.restoreDailyRecordsToRoom(context)
                val goalRestored = backupManager.restoreGoalHistoryToRoom(context)
                val trackingRestored = backupManager.restoreTrackingHistoryToRoom(context)

                Log.d("SharedViewModel", "복원 결과: daily=$dailyRestored, goal=$goalRestored, tracking=$trackingRestored")
// 🔹 복원된 추적앱을 다시 반영
                loadTrackedPackages()

                // ✅ 복원 후 UI 데이터 다시 로드
                repository.loadRealData(getApplication())

            } catch (e: Exception) {
                Log.e("GoogleLogin", "Firebase sign in failed: $e")
            }
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun getUserName(): String {
        val user = auth.currentUser
        return if (user != null && !user.isAnonymous) {
            "${user.displayName ?: "사용자"}님 환영합니다"
        } else {
            "게스트 로그인 중입니다"
        }
    }

    private fun isGoogleLogin(): Boolean {
        val user = auth.currentUser
        return user != null && !user.isAnonymous
    }

    // ----------------------- 메인 통계 -----------------------

    // 🔁 기존 코드 지우고 이걸로 교체
    val totalUsageData: LiveData<Pair<Int, Int>> =
        MediatorLiveData<Pair<Int, Int>>().apply {

            fun update() {
                val list = appUsageList.value ?: emptyList()
                val tracked = trackedPackages.value ?: emptySet()

                // 🔹 추적앱이 없으면 총 사용시간/목표 0으로
                val filtered = if (tracked.isEmpty()) {
                    emptyList<AppUsage>()
                } else {
                    // 🔹 선택한 앱들만 합산
                    list.filter { it.packageName in tracked }
                }

                val totalUsage = filtered.sumOf { it.currentUsage }
                val autoGoal = filtered.sumOf { it.goalTime }

                // 🔹 전체 목표시간이 설정되어 있으면 그걸 사용, 없으면 기존처럼 합산 사용
                val goal = _overallGoalMinutes.value ?: autoGoal

                value = totalUsage to goal
            }

            addSource(appUsageList) { update() }
            addSource(trackedPackages) { update() }
            addSource(overallGoalMinutes) { update() }   // 🔹 전체 목표 변경 시 재계산
        }

    private val filteredGoalTime: LiveData<Int> = MediatorLiveData<Int>().apply {

        fun update() {
            val goals = appUsageList.value ?: emptyList()
            val filterPkg = _selectedFilter.value
            val tracked = trackedPackages.value ?: emptySet()

            value = if (filterPkg == null) {
                // 🔹 전체보기: 추적 앱들의 목표 합 기준 + 전체 목표시간 우선
                val autoGoal =
                    if (tracked.isEmpty()) {
                        goals.sumOf { it.goalTime }
                    } else {
                        goals.filter { it.packageName in tracked }
                            .sumOf { it.goalTime }
                    }

                _overallGoalMinutes.value ?: autoGoal
            } else {
                // 🔹 특정 앱 보기: 해당 앱의 목표시간만
                goals.find { it.packageName == filterPkg }?.goalTime ?: 0
            }
        }

        addSource(appUsageList) { update() }
        addSource(_selectedFilter) { update() }
        addSource(overallGoalMinutes) { update() }
        addSource(trackedPackages) { update() }   // 🔹 추적앱 바뀌면 목표도 재계산
    }

    // 캘린더/그래프에서 쓸 현재 필터의 목표시간(분)
    val calendarGoalTime: LiveData<Int> = filteredGoalTime

    val calendarDecoratorData: LiveData<List<CalendarDecoratorData>> =
        MediatorLiveData<List<CalendarDecoratorData>>().apply {
            fun updateDecorators() {
                val goals = appUsageList.value ?: return
                val dailies = dailyUsageList.value ?: return
                val filterPkg = _selectedFilter.value
                val tracked = trackedPackages.value ?: emptySet()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
                val decorators = mutableListOf<CalendarDecoratorData>()

                for (daily in dailies) {
                    val date = sdf.parse(daily.date) ?: continue
                    val cal = Calendar.getInstance(); cal.time = date
                    val calDay = CalendarDay.from(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH)
                    )

                    val (usage, goal) = if (filterPkg == null) {
                        // ✅ 전체보기: "추적 앱들"의 총 사용시간 + 전체 목표시간 기준
                        val dayUsage = daily.appUsages
                            .filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }
                            .values
                            .sum()

                        val autoGoal = goals
                            .filter { tracked.isEmpty() || it.packageName in tracked }
                            .sumOf { it.goalTime }

                        val totalGoal = _overallGoalMinutes.value ?: autoGoal

                        dayUsage to totalGoal
                    } else {
                        // ✅ 특정 앱 보기: 해당 앱만
                        val dayUsage = daily.appUsages[filterPkg] ?: 0
                        val appGoal = goals.find { it.packageName == filterPkg }?.goalTime ?: 0
                        dayUsage to appGoal
                    }

                    if (goal <= 0) continue

                    val status = when {
                        usage > goal -> DayStatus.FAIL
                        usage > goal * 0.7 -> DayStatus.WARNING
                        else -> DayStatus.SUCCESS
                    }
                    decorators.add(CalendarDecoratorData(calDay, status))
                }
                value = decorators
            }

            addSource(dailyUsageList) { updateDecorators() }
            addSource(filteredGoalTime) { updateDecorators() }
            addSource(_selectedFilter) { updateDecorators() }
            addSource(overallGoalMinutes) { updateDecorators() }
            addSource(trackedPackages) { updateDecorators() }   // 🔹 추적앱 바뀔 때 갱신
        }


    // 선택된 달 (1~12), 기본값 = 현재 달
    private val _selectedMonth = MutableLiveData<Int>(
        Calendar.getInstance().get(Calendar.MONTH) + 1
    )
    val selectedMonth: LiveData<Int> = _selectedMonth

    // 캘린더에서 월이 바뀔 때 호출할 함수
    fun setSelectedMonth(year: Int, month: Int) {
        // month는 1~12 기준으로 받는다고 가정
        _selectedMonth.value = month
    }

    val calendarStatsText: LiveData<String> = MediatorLiveData<String>().apply {

        fun updateText() {
            val decorators = calendarDecoratorData.value ?: emptyList()
            val month = selectedMonth.value ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)

            val successDays = decorators.count {
                it.date.month == month &&
                        (it.status == DayStatus.SUCCESS || it.status == DayStatus.WARNING)
            }

            val filterPkg = _selectedFilter.value

            value = "${month}월 목표 성공일: 총 ${successDays}일!"
        }

        addSource(calendarDecoratorData) { updateText() }
        addSource(_selectedFilter) { updateText() }
        addSource(selectedMonth) { updateText() }   // 🔹 새로 추가
    }

    // 🔹 전체보기(필터 없음)일 때, 선택된 앱들의 총 사용시간 / 전체 목표시간으로 스트릭 계산
    private fun calculateOverallStreak(): Int {
        val dailies = dailyUsageList.value ?: return 0
        val tracked = trackedPackages.value ?: emptySet()

        // 전체 목표시간: 설정값 우선, 없으면 추적 앱들의 개별 목표 합
        val appList = appUsageList.value ?: emptyList()
        val trackedApps = if (tracked.isEmpty()) {
            emptyList<AppUsage>()
        } else {
            appList.filter { it.packageName in tracked }
        }

        val autoGoal = trackedApps.sumOf { it.goalTime }
        val goal = overallGoalMinutes.value ?: autoGoal
        if (goal <= 0) return 0

        // 날짜 내림차순 (가장 최근 날짜부터)
        val sortedDays = dailies.sortedByDescending { it.date }

        var wasSuccess: Boolean? = null
        var streakCount = 0

        for (day in sortedDays) {
            // 이 날짜의 "선택된 앱들" 총 사용시간
            val dayUsage = day.appUsages
                .filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }
                .values
                .sum()

            val success = dayUsage <= goal

            if (wasSuccess == null) {
                wasSuccess = success
            }

            if (success == wasSuccess) {
                streakCount++
            } else {
                break
            }
        }

        if (wasSuccess == null) return 0
        return if (wasSuccess == true) streakCount else -streakCount
    }

    val streakText: LiveData<String> = MediatorLiveData<String>().apply {
        fun updateStreak() {
            val filterPkg = _selectedFilter.value
            val appList = appUsageList.value ?: return

            // 🔍 streak 가져오기
            val streak = if (filterPkg == null) {
                // ✅ 전체보기: 선택된 앱들의 총 사용시간/전체 목표 기준으로 계산한 스트릭
                calculateOverallStreak()
            } else {
                // ✅ 개별 앱 보기: 해당 앱의 streak 그대로 사용
                appList.find { it.packageName == filterPkg }?.streak ?: 0
            }

            // 🔍 이름 표시
            val appName = if (filterPkg == null) {
                "전체"
            } else {
                appList.find { it.packageName == filterPkg }?.appLabel ?: "알 수 없음"
            }

            val days = kotlin.math.abs(streak)
            val emoji = if (streak >= 0) "🔥" else "💀"

            value = "$appName: $emoji$days"
        }

        addSource(appUsageList) { updateStreak() }
        addSource(_selectedFilter) { updateStreak() }
        // 🔹 전체보기 스트릭은 일별 사용량, 추적앱, 전체 목표가 바뀌어도 갱신돼야 함
        addSource(dailyUsageList) { updateStreak() }
        addSource(trackedPackages) { updateStreak() }
        addSource(overallGoalMinutes) { updateStreak() }
    }

    val chartData: LiveData<List<Entry>> = MediatorLiveData<List<Entry>>().apply {
        fun updateChart() {
            val dailies = dailyUsageList.value ?: emptyList()
            val filterPkg = _selectedFilter.value
            val tracked = trackedPackages.value ?: emptySet()
            val month = selectedMonth.value ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)
            val entries = mutableListOf<Entry>()

            dailies
                .filter { it.date.substring(5, 7).toInt() == month }
                .forEach { daily ->
                    val dayOfMonth = daily.date.substring(8, 10).toFloat()
                    val usage = if (filterPkg == null) {
                        // ✅ 전체보기: 추적앱만 합산
                        daily.appUsages
                            .filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }
                            .values
                            .sum()
                    } else {
                        // ✅ 특정 앱 보기
                        daily.appUsages[filterPkg] ?: 0
                    }
                    entries.add(Entry(dayOfMonth, usage.toFloat()))
                }
            value = entries
        }

        addSource(dailyUsageList) { updateChart() }
        addSource(_selectedFilter) { updateChart() }
        addSource(selectedMonth) { updateChart() }
        addSource(trackedPackages) { updateChart() }   // 🔹 추적앱 변경 반영
    }



    // ----------------------- 데이터 로딩/설정 저장 -----------------------

    // SharedViewModel.kt - refreshData() 수정된 코드
// SharedViewModel.kt - refreshData() 최종 수정 코드
    fun refreshData() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrEmpty()) {
                // ✅ IO에서 Room 데이터 로드
                withContext(Dispatchers.IO) {
                    repository.loadRealData(getApplication())
                }
            } else {
                // ✅ 게스트 상태에서는 Prefs 기반 데이터만 사용
            }
        }
    }


    fun setGoalTimes(goals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val dao = AppDatabase.getInstance(getApplication()).historyDao()

        goals.forEach { (pkg, minutes) ->
            // ✅ packageName이 null/빈 문자열이면 "overall"로 대체
            val safePackageName = if (!pkg.isNullOrBlank()) pkg else "overall"

            val entity = GoalHistoryEntity(
                uid = uid,
                effectiveDate = todayString(),
                packageName = safePackageName,
                goalMinutes = minutes
            )

            // ✅ Room 저장
            dao.insertGoalHistory(entity)

            // ✅ Firestore 백업 추가
            if (uid != "anonymous") {
                BackupManager().backupGoalHistory(uid, entity)
            }
        }

        // 저장 후 다시 데이터 로드
        refreshData()
    }

    fun setCalendarFilter(packageName: String? = null) {
        _selectedFilter.value = packageName
    }


    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings, getApplication())
    }

    private fun todayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        return sdf.format(Date())
    }

}
