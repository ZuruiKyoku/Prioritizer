package com.slygames.prioritizer.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0") suspend fun getProfile(): UserProfile?

    @Query("SELECT * FROM user_profile WHERE id = 0") fun observeProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: UserProfile)

    @Query("DELETE FROM user_profile") suspend fun clearProfile()
}
