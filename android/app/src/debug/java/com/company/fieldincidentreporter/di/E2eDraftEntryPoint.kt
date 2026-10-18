package com.company.fieldincidentreporter.di

import com.company.fieldincidentreporter.data.offline.DraftReports
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Exposes the app's draft store to connected tests in debug builds. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface E2eDraftEntryPoint {
    fun drafts(): DraftReports
}
