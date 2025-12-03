package com.example.pixeldiet.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. 사용자 프로필 (닉네임 + 프로필 이미지)
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,          // Firebase UID
    val nickname: String,                 // 닉네임
    val profileImageUri: String? = null   // 프로필 이미지 (URI 또는 Base64 문자열)
)

// 2. 친구 관계
@Entity(tableName = "friends", primaryKeys = ["ownerUid", "friendUid"])
data class FriendEntity(
    val ownerUid: String,     // 내 UID
    val friendUid: String,    // 친구 UID
    val sinceDate: String     // 친구 추가한 날짜
)

// 3. 그룹 정보 (공통 목표 시간)
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val groupName: String,    // 그룹 이름
    val goalMinutes: Int,     // 그룹 공통 목표 시간
    val createdDate: String   // 생성 날짜
)

// 4. 그룹 멤버 정보 (경쟁 기록)
@Entity(tableName = "group_members", primaryKeys = ["groupId", "memberUid"])
data class GroupMemberEntity(
    val groupId: Long,        // 그룹 ID
    val memberUid: String,    // 멤버 UID
    val joinDate: String,     // 가입 날짜
    val successCount: Int = 0,// 목표 달성 횟수
    val failCount: Int = 0    // 목표 실패 횟수
)