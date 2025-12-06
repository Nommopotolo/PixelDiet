package com.example.pixeldiet

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log   // 🔹 로그캣 사용을 위한 import
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope   // 🔹 코루틴 실행을 위한 import
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.pixeldiet.ui.navigation.AppNavigation
import com.example.pixeldiet.ui.notification.NotificationHelper
import com.example.pixeldiet.ui.theme.PixelDietTheme
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.example.pixeldiet.worker.UsageCheckWorker
import kotlinx.coroutines.delay              // 🔹 지연을 위한 import
import kotlinx.coroutines.launch            // 🔹 코루틴 실행을 위한 import
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: SharedViewModel by viewModels()

    // 알림 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한 허용됨 -> WorkManager 시작
            startUsageCheckWorker()
        } else {
            Log.w("MainActivity", "알림 권한 거부됨")   // 🔹 권한 거부 로그
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "앱 시작됨 (MainActivity onCreate)")   // 🔹 앱 시작 로그

        // ⭐️ 1. 알림 채널 생성
        NotificationHelper.createNotificationChannel(this)

        // ⭐️ 2. 알림 권한 확인
        checkNotificationPermission()

        // ⭐️ 3. 사용 시간 권한 확인
        checkUsageStatsPermission()

        // ✅ Repository는 ViewModel을 모름 → 여기서 attachAuthListener만 등록
        com.example.pixeldiet.repository.UsageRepository.attachAuthListener(this)

        setContent {
            PixelDietTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "앱이 포그라운드로 돌아옴 (onResume)")
        if (hasUsageStatsPermission()) {
            viewModel.refreshData()
            lifecycleScope.launch {
                // 🔹 Firestore → Room 복원 먼저 실행
                val backupManager = com.example.pixeldiet.backup.BackupManager()
                backupManager.restoreDailyRecordsToRoom(applicationContext)
                backupManager.restoreGoalHistoryToRoom(applicationContext)
                backupManager.restoreTrackingHistoryToRoom(applicationContext)

                // 🔹 복원 완료 후 UsageRepository 재로딩
                com.example.pixeldiet.repository.UsageRepository.loadRealData(applicationContext)
                Log.d("MainActivity", "복원 후 UsageRepository.loadRealData 호출 완료")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "앱 종료됨 (MainActivity onDestroy)")   // 🔹 앱 종료 로그
    }

    // 알림 권한 확인 및 요청 함수
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33) 이상
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 이하는 권한이 자동 허용
            startUsageCheckWorker()
        }
    }

    // ⭐️ 알림 및 워커 설정 함수
    private fun startUsageCheckWorker() {
        val usageCheckWorkRequest =
            PeriodicWorkRequest.Builder(UsageCheckWorker::class.java, 15, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UsageCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            usageCheckWorkRequest
        )
        Log.d("MainActivity", "UsageCheckWorker 시작됨")   // 🔹 워커 시작 로그
    }

    // --- 사용 시간 권한 확인 로직 ---
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            Log.w("MainActivity", "사용 정보 접근 권한 없음")   // 🔹 권한 없음 로그
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("앱 사용 시간 정보를 가져오기 위해 '사용 정보 접근' 권한이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            Log.d("MainActivity", "사용 정보 접근 권한 있음")   // 🔹 권한 있음 로그
        }
    }
}