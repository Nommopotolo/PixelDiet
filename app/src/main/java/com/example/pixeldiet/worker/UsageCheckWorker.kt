package com.example.pixeldiet.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pixeldiet.model.AppUsage // ⭐️ import AppUsage
import com.example.pixeldiet.model.NotificationSettings
import com.example.pixeldiet.repository.NotificationPrefs // ⭐️ [신규] import
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.ui.notification.NotificationHelper

class UsageCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // ⭐️ [신규] NotificationPrefs 인스턴스
    private val prefs = NotificationPrefs(context)

    override suspend fun doWork(): Result {
        try {
            val repository = UsageRepository

            // 1. Repository의 데이터를 최신으로 새로고침 (실제 사용 시간 가져오기)
            repository.loadRealData(context)

            // 2. 최신 데이터 가져오기 (SharedPreferences에서도 설정을 읽음)
            val appList = repository.appUsageList.value
            val settings = prefs.loadNotificationSettings() // ⭐️ Repository가 아닌 Prefs에서 직접 읽음

            if (appList.isNullOrEmpty()) {
                return Result.failure() // 데이터가 없으면 실패
            }

            // 3. 알림 조건 확인
            checkIndividualAppAlerts(appList, settings)
            checkTotalAppAlerts(appList, settings)

            return Result.success()

        } catch (e: Exception) {
            return Result.failure()
        }
    }

    // ⭐️ [수정됨] 개별 앱 알림 (정밀 로직)
    private fun checkIndividualAppAlerts(
        appList: List<AppUsage>,
        settings: NotificationSettings
    ) {
        val now = System.currentTimeMillis() // 현재 시간

        for (app in appList) {
            if (app.goalTime == 0) continue
            val usage = app.currentUsage
            val goal = app.goalTime
            val percentage = (usage.toFloat() / goal) * 100

            val intervalMillis = settings.repeatIntervalMinutes * 60 * 1000 // 분 -> 밀리초

            // ⭐️ 100% 초과 알림
            val type100 = "ind_100_${app.appName.name}" // 예: "ind_100_YOUTUBE"
            if (settings.individualApp100 && percentage >= 100) {
                val lastSent = prefs.getLastRepeatSentTime(type100)
                if (now - lastSent > intervalMillis) { // 설정한 반복 시간이 지났으면
                    NotificationHelper.showNotification(
                        context,
                        "${app.appName.displayName} 멈춰!",
                        "목표 시간 ${formatTime(goal)} / 사용 ${formatTime(usage)}"
                    )
                    prefs.recordRepeatSentTime(type100) // ⭐️ 알림 보낸 시간 기록
                }
            }
            // ⭐️ 70% 도달 알림
            val type70 = "ind_70_${app.appName.name}" // 예: "ind_70_YOUTUBE"
            if (settings.individualApp70 && percentage >= 70 && !prefs.hasSentToday(type70)) {
                NotificationHelper.showNotification(
                    context,
                    "${app.appName.displayName} 70% 사용",
                    "목표 사용시간을 70% 사용했어요!"
                )
                prefs.recordSentToday(type70) // ⭐️ 오늘 보냈다고 기록 (하루 한 번)
            }
            // ⭐️ 50% 도달 알림
            val type50 = "ind_50_${app.appName.name}" // 예: "ind_50_YOUTUBE"
            if (settings.individualApp50 && percentage >= 50 && !prefs.hasSentToday(type50)) {
                NotificationHelper.showNotification(
                    context,
                    "${app.appName.displayName} 50% 사용",
                    "목표 사용시간을 50% 사용했어요!"
                )
                prefs.recordSentToday(type50) // ⭐️ 오늘 보냈다고 기록 (하루 한 번)
            }
        }
    }

    // ⭐️ [수정됨] 전체 앱 알림 (정밀 로직)
    private fun checkTotalAppAlerts(
        appList: List<AppUsage>,
        settings: NotificationSettings
    ) {
        val totalUsage = appList.sumOf { it.currentUsage }
        val totalGoal = appList.sumOf { it.goalTime }
        if (totalGoal == 0) return

        val percentage = (totalUsage.toFloat() / totalGoal) * 100
        val now = System.currentTimeMillis()
        val intervalMillis = settings.repeatIntervalMinutes * 60 * 1000

        // ⭐️ 100% 초과
        val type100 = "total_100"
        if (settings.total100 && percentage >= 100) {
            val lastSent = prefs.getLastRepeatSentTime(type100)
            if (now - lastSent > intervalMillis) {
                NotificationHelper.showNotification(
                    context,
                    "전체 시간 초과!",
                    "전체 목표 ${formatTime(totalGoal)} / 사용 ${formatTime(totalUsage)}"
                )
                prefs.recordRepeatSentTime(type100) // ⭐️ 시간 기록
            }
        }
        // ⭐️ 70%
        val type70 = "total_70"
        if (settings.total70 && percentage >= 70 && !prefs.hasSentToday(type70)) {
            NotificationHelper.showNotification(
                context,
                "전체 시간 70% 사용",
                "전체 목표사용시간을 70% 사용했어요!"
            )
            prefs.recordSentToday(type70) // ⭐️ 날짜 기록
        }
        // ⭐️ 50%
        val type50 = "total_50"
        if (settings.total50 && percentage >= 50 && !prefs.hasSentToday(type50)) {
            NotificationHelper.showNotification(
                context,
                "전체 시간 50% 사용",
                "전체 목표사용시간을 50% 사용했어요!"
            )
            prefs.recordSentToday(type50) // ⭐️ 날짜 기록
        }
    }

    private fun formatTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%d시간 %02d분", hours, mins)
    }
}