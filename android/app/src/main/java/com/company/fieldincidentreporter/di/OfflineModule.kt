package com.company.fieldincidentreporter.di

import android.content.Context
import androidx.room.Room
import com.company.fieldincidentreporter.data.offline.DraftReportStore
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.data.offline.DraftReportDao
import com.company.fieldincidentreporter.data.offline.DraftReportDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DraftReportDatabase =
        Room.databaseBuilder(context, DraftReportDatabase::class.java, "draft-reports.db").build()

    @Provides
    fun dao(database: DraftReportDatabase): DraftReportDao = database.drafts()

    @Provides
    @Singleton
    fun draftStore(@ApplicationContext context: Context, dao: DraftReportDao, json: Json): DraftReportStore =
        DraftReportStore(File(context.filesDir, "drafts"), dao, json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineBindings {
    @Binds
    abstract fun drafts(store: DraftReportStore): DraftReports
}
