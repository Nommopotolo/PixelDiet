package com.example.pixeldiet.backup

import android.content.Context
import android.util.Log
import com.example.pixeldiet.database.AppDatabase
import com.example.pixeldiet.database.entity.DailyUsageEntity
import com.example.pixeldiet.database.entity.GoalHistoryEntity
import com.example.pixeldiet.database.entity.TrackingHistoryEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.SetOptions
import com.example.pixeldiet.repository.UsageRepository
class BackupManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** 현재 UID 가져오기 (익명/Google 상관없이) */
    fun currentUserId(): String {
        val user = auth.currentUser
        return if (user == null || user.isAnonymous) "anonymous" else user.uid
    }

    /** 익명 로그인 (앱 최초 실행 시) */
    suspend fun initUser() {
        Log.d("BackupManager", "앱 시작됨 (initUser 호출)")   // 시작 로그
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d("BackupManager", "익명 로그인 완료")
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "익명 로그인 실패", e)
        }
        Log.d("BackupManager", "앱 종료됨 (initUser 완료)")   // 종료 로그
    }

    /** Google 로그인 + 익명 데이터 자동 병합 */
    suspend fun signInWithGoogle(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val previousUser = auth.currentUser
            val previousUid = previousUser?.uid

            auth.signInWithCredential(credential).await()  // Google 로그인
            val currentUid = auth.currentUser?.uid ?: return

            Log.d("BackupManager", "구글 로그인 완료: $currentUid")

            // TODO: 익명 UID → Google UID 데이터 병합 로직 추가 필요
            // 예: Firestore에서 previousUid 데이터를 읽어와 currentUid로 복사

        } catch (e: Exception) {
            Log.e("BackupManager", "구글 로그인 실패", e)
        }
    }

    /** 백업 존재 여부 */
    suspend fun hasBackupData(): Boolean {
        val uid = currentUserId()
        if (uid.isBlank() || uid == "anonymous") return false

        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .limit(1)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            Log.e("BackupManager", "백업 데이터 확인 실패", e)
            false
        }
    }

    // GoalHistoryEntity Firestore 백업
    suspend fun backupGoalHistory(uid: String, entity: GoalHistoryEntity) {
        if (uid == "anonymous") {
            Log.d("BackupManager", "게스트 모드: GoalHistory Firestore 백업 건너뜀")
            return
        }

        if (entity.goalMinutes <= 0) {
            Log.d("BackupManager", "goalMinutes=0 → Firestore 업데이트 건너뜀")
            return
        }

        try {
            // ✅ packageName이 null 또는 빈 문자열일 경우 "overall"로 대체
            val safePackageName = if (!entity.packageName.isNullOrBlank()) entity.packageName!! else "overall"
            val docId = "${entity.effectiveDate}_$safePackageName"

            val data = mapOf(
                "effectiveDate" to entity.effectiveDate,
                "packageName" to safePackageName,   // ✅ 항상 저장
                "goalMinutes" to entity.goalMinutes
            )

            firestore.collection("users")
                .document(uid)
                .collection("goalHistory")
                .document(docId)
                .set(data, SetOptions.merge())
                .await()

            Log.d("BackupManager", "goalHistory Firestore 백업 성공: $docId")
        } catch (e: Exception) {
            Log.e("BackupManager", "goalHistory Firestore 백업 실패", e)
        }
    }



    // DailyUsageEntity Firestore 백업
    suspend fun backupDailyUsage(uid: String, entity: DailyUsageEntity) {
        if (uid == "anonymous") {
            Log.d("BackupManager", "게스트 모드: DailyUsage Firestore 백업 건너뜀")
            return
        }
        try {
            val data = mapOf(
                "date" to entity.date,
                "appUsages" to entity.appUsages
            )

            firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .document(entity.date)
                .set(data, SetOptions.merge())   // ✅ 필요한 필드만 저장
                .await()

            Log.d("BackupManager", "dailyRecords Firestore 백업 성공: ${entity.date}")
        } catch (e: Exception) {
            Log.e("BackupManager", "dailyRecords Firestore 백업 실패", e)
        }
    }

    // TrackingHistoryEntity Firestore 백업
    suspend fun backupTrackingHistory(uid: String, entity: TrackingHistoryEntity) {
        if (uid == "anonymous") {
            Log.d("BackupManager", "게스트 모드: TrackingHistory Firestore 백업 건너뜀")
            return
        }

        if (entity.trackedPackages.isEmpty()) {
            Log.d("BackupManager", "빈 trackedPackages → Firestore 업데이트 건너뜀")
            return
        }

        try {
            val data = mapOf(
                "effectiveDate" to entity.effectiveDate,
                "trackedPackages" to entity.trackedPackages
            )

            firestore.collection("users")
                .document(uid)
                .collection("trackingHistory")
                .document(entity.effectiveDate)
                .set(data, SetOptions.merge())   // ✅ 필요한 필드만 저장
                .await()

            Log.d("BackupManager", "trackingHistory Firestore 백업 성공: ${entity.effectiveDate}")
        } catch (e: Exception) {
            Log.e("BackupManager", "trackingHistory Firestore 백업 실패", e)
        }
    }


    // ✅ Firestore → Room 복원 (DailyUsage)
    suspend fun restoreDailyRecordsToRoom(context: Context): Boolean {
        val uid = currentUserId()
        if (uid == "anonymous") return false

        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            if (snapshot.isEmpty) return false

            val dao = AppDatabase.getInstance(context).historyDao()
            for (doc in snapshot.documents) {
                // ✅ 필드 "date"가 있으면 우선 사용, 없으면 문서 ID 사용
                val date = doc.getString("date") ?: doc.id
                val raw = doc.get("appUsages") as? Map<String, Any> ?: emptyMap()
                val appUsages = raw.mapValues { (it.value as Number).toInt() }

                dao.upsertDailyUsage(
                    DailyUsageEntity(uid = uid, date = date, appUsages = appUsages)
                )
            }
            Log.d("BackupManager", "dailyRecords Firestore → Room 복원 성공")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "dailyRecords Firestore 복원 실패", e)
            false
        }
    }

    // ✅ Firestore → Room 복원 (GoalHistory)
    suspend fun restoreGoalHistoryToRoom(context: Context): Boolean {
        val uid = currentUserId()
        if (uid == "anonymous") return false

        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("goalHistory")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            if (snapshot.isEmpty) return false

            val dao = AppDatabase.getInstance(context).historyDao()
            for (doc in snapshot.documents) {
                // ✅ 필드 우선, 없으면 문서 ID 사용
                val effectiveDate = doc.getString("effectiveDate") ?: doc.id
                val packageName = doc.getString("packageName")
                val goalMinutes = (doc.get("goalMinutes") as? Number)?.toInt() ?: 0

                dao.insertGoalHistory(
                    GoalHistoryEntity(
                        uid = uid,
                        effectiveDate = effectiveDate,
                        packageName = packageName,
                        goalMinutes = goalMinutes
                    )
                )
            }
            Log.d("BackupManager", "goalHistory Firestore → Room 복원 성공")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "goalHistory Firestore 복원 실패", e)
            false
        }
    }

    // ✅ Firestore → Room 복원 (TrackingHistory)
    // ✅ Firestore → Room 복원 (TrackingHistory) + UsageRepository 재로딩 추가
    suspend fun restoreTrackingHistoryToRoom(context: Context): Boolean {
        val uid = currentUserId()
        if (uid == "anonymous") return false

        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("trackingHistory")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            if (snapshot.isEmpty) return false

            val dao = AppDatabase.getInstance(context).historyDao()
            for (doc in snapshot.documents) {
                val effectiveDate = doc.getString("effectiveDate") ?: doc.id
                val trackedPackages = doc.get("trackedPackages") as? List<String> ?: emptyList()

                dao.insertTrackingHistory(
                    TrackingHistoryEntity(
                        uid = uid,
                        effectiveDate = effectiveDate,
                        trackedPackages = trackedPackages
                    )
                )
            }
            Log.d("BackupManager", "trackingHistory Firestore → Room 복원 성공")

            true
        } catch (e: Exception) {
            Log.e("BackupManager", "trackingHistory Firestore 복원 실패", e)
            false
        }
    }
}