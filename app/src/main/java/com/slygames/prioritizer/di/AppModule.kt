package com.slygames.prioritizer.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.task.TaskDao
import com.slygames.prioritizer.data.user.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Central Hilt wiring for framework/singleton objects (Room, Firebase). Repositories are
 * constructor-injected directly (`@Inject constructor` + `@Singleton`) so they don't need entries
 * here -- only things Hilt can't construct on its own (external SDK classes, Room's generated
 * database) are provided explicitly.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
            AppDatabase.getInstance(context)

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}
