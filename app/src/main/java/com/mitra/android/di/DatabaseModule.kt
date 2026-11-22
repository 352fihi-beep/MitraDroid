package com.mitra.android.di

import android.content.Context
import androidx.room.Room
import com.mitra.android.database.AppDatabase
import com.mitra.android.database.CallRecordDao
import com.mitra.android.database.MemoryDao
import com.mitra.android.database.SmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mitra_database_10x"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.dao()

    @Provides
    fun provideSmsDao(database: AppDatabase): SmsDao = database.smsDao()

    @Provides
    fun provideCallRecordDao(database: AppDatabase): CallRecordDao = database.callRecordDao()
}
