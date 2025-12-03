package com.example.pixeldiet.database.dao

import androidx.room.*
import com.example.pixeldiet.database.entity.*

@Dao
interface SocialDao {

    // --- User Profile ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    suspend fun getUserProfile(uid: String): UserProfileEntity?

    // --- Friends ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriend(friend: FriendEntity)

    @Query("SELECT * FROM friends WHERE ownerUid = :uid")
    suspend fun getFriends(uid: String): List<FriendEntity>

    // --- Groups ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createGroup(group: GroupEntity): Long

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroup(groupId: Long): GroupEntity?

    // --- Group Members ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGroupMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMembers(groupId: Long): List<GroupMemberEntity>
}