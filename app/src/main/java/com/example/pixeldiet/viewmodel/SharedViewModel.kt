package com.example.pixeldiet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.model.*
import com.example.pixeldiet.repository.UsageRepository
import com.github.mikephil.charting.data.Entry
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository
    val appUsageList: LiveData<List<AppUsage>> = repository.appUsageList
    private val dailyUsageList: LiveData<List<DailyUsage>> = repository.dailyUsageList
    val notificationSettings: LiveData<NotificationSettings> = repository.notificationSettings

    private val _selectedFilter = MutableLiveData<AppName?>(null)

    // ... (TotalUsageData, FilteredGoalTime 등 기존 코드 동일하게 유지) ...
    // 기존 코드의 MediatorLiveData 부분들은 그대로 두시면 됩니다.
    // 여기서는 지면 관계상 생략하지만, 원본 코드의 로직을 그대로 유지하세요.
    val totalUsageData: LiveData<Pair<Int, Int>> = appUsageList.map { list ->
        val totalUsage = list.sumOf { it.currentUsage }
        val totalGoal = list.sumOf { it.goalTime }
        Pair(totalUsage, totalGoal)
    }

    // ... (중략: calendarDecoratorData, chartData 등 기존 로직 유지) ...
    val calendarDecoratorData = MediatorLiveData<List<CalendarDecoratorData>>() // (구현 내용은 기존 유지)
    val calendarStatsText = MediatorLiveData<String>() // (구현 내용은 기존 유지)
    val streakText = MediatorLiveData<String>() // (구현 내용은 기존 유지)
    val chartData = MediatorLiveData<List<Entry>>() // (구현 내용은 기존 유지)


    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            // [중요] Repository에 Application Context를 전달하여 DB 접근 가능하게 함
            repository.loadRealData(getApplication())
        }
    }

    // [중요] 목표 설정 시 Repository의 updateGoalTimes 호출 -> DB 저장됨
    fun setGoalTimes(goals: Map<AppName, Int>) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateGoalTimes(goals)
    }

    fun setCalendarFilter(filterName: String) {
        _selectedFilter.value = when (filterName) {
            "네이버 웹툰" -> AppName.NAVER_WEBTOON
            "인스타그램" -> AppName.INSTAGRAM
            "유튜브" -> AppName.YOUTUBE
            else -> null
        }
    }

    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings, getApplication())
    }
}