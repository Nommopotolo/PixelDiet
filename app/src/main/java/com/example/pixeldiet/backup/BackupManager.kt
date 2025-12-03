package com.example.pixeldiet.backup


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BackupManager() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** 현재 UID 가져오기 (익명/Google 상관없이) */
    fun currentUserId(): String = auth.currentUser?.uid ?: "anonymous"

    /** 익명 로그인 (앱 최초 실행 시) */
    suspend fun initUser() {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Google 로그인 + 익명 데이터 자동 병합 */
    suspend fun signInWithGoogle(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val previousUser = auth.currentUser
            val previousUid = previousUser?.uid

            auth.signInWithCredential(credential).await()  // Google 로그인
            val currentUid = auth.currentUser?.uid ?: return


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /** 백업 존재 여부 */
    suspend fun hasBackupData(): Boolean {
        val uid = currentUserId()
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("dailyRecords")
            .get()
            .await()
        return snapshot.documents.isNotEmpty()
    }

}

