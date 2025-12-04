package com.example.pixeldiet.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pixeldiet.MainActivity
import com.example.pixeldiet.R
import com.example.pixeldiet.repository.UsageRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var backupManager: BackupManager

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    lifecycleScope.launch {
                        try {
                            // 1. Firebase 인증
                            backupManager.signInWithGoogle(idToken)

                            // 2. UID 저장
                            val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
                            if (firebaseUid != null) {
                                getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("uid", firebaseUid)
                                    .apply()
                            }

                            // 3. Firestore → Room 복원
                            // Firestore → Room 복원
                            val dailyOk = backupManager.restoreDailyRecordsToRoom(this@LoginActivity)
                            val goalOk = backupManager.restoreGoalHistoryToRoom(this@LoginActivity)
                            val trackingOk = backupManager.restoreTrackingHistoryToRoom(this@LoginActivity)

// Room → LiveData 즉시 반영 (DailyUsage)
                            UsageRepository.loadOnceAfterRestore(this@LoginActivity)

// ✅ Goal/Tracking도 즉시 반영
                            val vm = androidx.lifecycle.ViewModelProvider(this@LoginActivity)
                                .get(com.example.pixeldiet.viewmodel.SharedViewModel::class.java)
                            vm.refreshData()

                            android.util.Log.d(
                                "LoginActivity",
                                "복원 결과: daily=$dailyOk, goal=$goalOk, tracking=$trackingOk"
                            )

                            // 5. 사용자 알림 및 화면 이동
                            Toast.makeText(
                                this@LoginActivity,
                                "구글 로그인 및 데이터 복원 완료",
                                Toast.LENGTH_SHORT
                            ).show()
                            goToMain()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(
                                this@LoginActivity,
                                "구글 로그인 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LoginActivity, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        backupManager = BackupManager()

        val btnGuest = findViewById<Button>(R.id.btnGuest)
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)

        // 구글 로그인 옵션
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 게스트 로그인
        btnGuest.setOnClickListener {
            lifecycleScope.launch {
                try {
                    backupManager.initUser() // 익명 로그인 생성
                    val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

                    getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("uid", firebaseUid)
                        .apply()

                    Toast.makeText(this@LoginActivity, "게스트 로그인 완료", Toast.LENGTH_SHORT).show()
                    goToMain()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@LoginActivity, "로그인 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 구글 로그인
        btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}