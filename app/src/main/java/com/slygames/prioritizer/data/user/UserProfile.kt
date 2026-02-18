package com.slygames.prioritizer.data.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val nickname: String,
    val profileImagePath: String?, // local
    val profileImageRemoteUrl: String?, // firebase URL
    val friendCode: String = ""
)