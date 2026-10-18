package com.company.fieldincidentreporter.data.offline

import com.company.fieldincidentreporter.data.ReportInput
import kotlinx.coroutines.flow.Flow

interface PendingReports {
    fun observe(): Flow<List<PendingReport>>
    suspend fun queue(input: ReportInput)
    suspend fun getInput(id: String): ReportInput?
    suspend fun markSending(id: String)
    suspend fun mark(id: String, state: DeliveryState)
    suspend fun complete(id: String)
    suspend fun retry(id: String)
    suspend fun discard(id: String)
    fun ensureScheduled(reports: List<PendingReport>)
}
