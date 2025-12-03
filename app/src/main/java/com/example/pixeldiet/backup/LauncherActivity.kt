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

        // Room DB 생성


        // BackupManager 초기화
        backupManager = BackupManager()

        // 앱 시작 시 순차 처리
        lifecycleScope.launch {
            handleUserAndBackup()
            Log.d("LauncherActivity", "restoreData 호출 완료")
        }
    }

    private suspend fun handleUserAndBackup() {
        try {
            val auth = FirebaseAuth.getInstance()
            var firebaseUser = auth.currentUser

            // 현재 UID 없으면 익명 로그인
            if (firebaseUser == null) {
                backupManager.initUser()
                firebaseUser = auth.currentUser
            }



            // 익명 사용자든 구글 사용자든 로그인된 상태면 바로 Main으로 이동
            if (firebaseUser != null) {
                goToMain()
            } else {
                goToLogin()
            }

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
