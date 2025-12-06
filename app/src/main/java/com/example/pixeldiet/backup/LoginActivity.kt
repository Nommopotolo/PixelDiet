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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.util.Log

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
                            // Firebase 인증 진행
                            backupManager.signInWithGoogle(idToken)

                            // ✅ Firebase UID 가져오기
                            val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
                            if (firebaseUid != null) {
                                getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("uid", firebaseUid) // ✅ Firebase UID 저장
                                    .apply()
                            }

                            // 🔹 Firestore → Room 복원 추가
                            val ctx = applicationContext
                            val dailyRestored = backupManager.restoreDailyRecordsToRoom(ctx)
                            val goalRestored = backupManager.restoreGoalHistoryToRoom(ctx)
                            val trackingRestored = backupManager.restoreTrackingHistoryToRoom(ctx)

                            Log.d("LoginActivity", "복원 결과: daily=$dailyRestored, goal=$goalRestored, tracking=$trackingRestored")
// 🔹 Firestore 복원 후 즉시 Repository와 ViewModel 갱신
                            com.example.pixeldiet.repository.UsageRepository.loadRealData(ctx)

// SharedViewModel도 강제로 refreshData 호출
                            val vm = androidx.lifecycle.ViewModelProvider(this@LoginActivity)
                                .get(com.example.pixeldiet.viewmodel.SharedViewModel::class.java)
                            vm.refreshData()

                            Toast.makeText(this@LoginActivity, "구글 로그인 완료", Toast.LENGTH_SHORT).show()
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
        Log.d("LoginActivity", "앱 시작됨 (LoginActivity onCreate)")   // 🔹 앱 시작 로그
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

                    // ✅ UID SharedPreferences에 저장 (익명 UID 사용)
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LoginActivity", "앱 종료됨 (LoginActivity onDestroy)")   // 🔹 앱 종료 로그
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}