package com.example.pixeldiet.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// 친구 데이터를 담을 모델
data class Friend(
    val uid: String = "",
    val email: String = "",
    val name: String = ""
)

object FriendRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 1. 친구 추가 (이메일로 사용자 검색 후 추가)
    suspend fun addFriendByEmail(email: String): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("로그인이 필요합니다."))

        return try {
            // A. 이메일로 해당 유저 찾기 (users 컬렉션에 email 필드가 있다고 가정)
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("해당 이메일을 가진 사용자를 찾을 수 없습니다."))
            }

            val targetUserDoc = querySnapshot.documents[0]
            val targetUid = targetUserDoc.id
            val targetEmail = targetUserDoc.getString("email") ?: ""
            // 이름이나 닉네임 필드가 있다면 가져오기

            // B. 내 친구 목록(서브컬렉션)에 추가
            val friendData = hashMapOf(
                "uid" to targetUid,
                "email" to targetEmail,
                "addedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(currentUser.uid)
                .collection("friends").document(targetUid)
                .set(friendData)
                .await()

            Result.success("친구가 추가되었습니다.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 내 친구 목록 가져오기
    suspend fun getFriendList(): List<Friend> {
        val currentUser = auth.currentUser ?: return emptyList()

        return try {
            val snapshot = firestore.collection("users").document(currentUser.uid)
                .collection("friends")
                .get()
                .await()

            snapshot.documents.map { doc ->
                Friend(
                    uid = doc.id,
                    email = doc.getString("email") ?: "",
                    name = doc.getString("name") ?: "알 수 없음"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}