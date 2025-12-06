package com.example.pixeldiet.backup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.util.Log
import com.example.pixeldiet.MainActivity

class LauncherActivity : AppCompatActivity() {

    private lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backupManager = BackupManager()

        lifecycleScope.launch {
            handleUserAndBackup()
            Log.d("LauncherActivity", "restoreData 호출 완료")
        }
    }

    private suspend fun handleUserAndBackup() {
        try {
            val auth = FirebaseAuth.getInstance()
            val firebaseUser = auth.currentUser

            if (firebaseUser == null) {
                // 로그인 화면으로 이동 (구글/게스트 선택)
                goToLogin()
                return
            }

            if (firebaseUser.isAnonymous) {
                // ✅ 게스트 모드: Firestore 복원 건너뜀
                Log.d("LauncherActivity", "게스트 모드: Room DB만 사용")
            } else {
                // ✅ 구글 로그인 모드: Firestore 복원 실행
                val hasBackup = backupManager.hasBackupData()
                if (hasBackup) {
                    val restoredDaily = backupManager.restoreDailyRecordsToRoom(applicationContext)
                    val restoredGoal = backupManager.restoreGoalHistoryToRoom(applicationContext)
                    val restoredTracking = backupManager.restoreTrackingHistoryToRoom(applicationContext)

                    if (restoredDaily || restoredGoal || restoredTracking) {
                        Log.d("LauncherActivity", "Firestore 데이터 복원 성공")
                        // ✅ Activity 컨텍스트를 넘겨야 함
                        val vm = androidx.lifecycle.ViewModelProvider(this@LauncherActivity)
                            .get(com.example.pixeldiet.viewmodel.SharedViewModel::class.java)
                        vm.refreshData()
                    } else {
                        Log.d("LauncherActivity", "Firestore 데이터 없음 또는 복원 실패")
                    }
                }
            }

            goToMain()

        } catch (e: Exception) {
            e.printStackTrace()
            goToLogin()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}