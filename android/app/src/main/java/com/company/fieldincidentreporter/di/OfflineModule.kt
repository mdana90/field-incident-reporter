package com.company.fieldincidentreporter.di

import android.content.Context
import androidx.room.Room
import com.company.fieldincidentreporter.data.offline.PendingReportDao
import com.company.fieldincidentreporter.data.offline.PendingReportDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PendingReportDatabase =
        Room.databaseBuilder(context, PendingReportDatabase::class.java, "pending-reports.db").build()

    @Provides
    fun dao(database: PendingReportDatabase): PendingReportDao = database.pendingReports()
}
