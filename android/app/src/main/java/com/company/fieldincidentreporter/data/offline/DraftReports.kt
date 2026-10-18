package com.company.fieldincidentreporter.data.offline

import com.company.fieldincidentreporter.data.ReportInput
import kotlinx.coroutines.flow.Flow

enum class DraftState { DRAFT, UNCONFIRMED }

data class DraftReport(
    val id: String,
    val input: ReportInput,
    val state: DraftState,
    val createdAt: Long,
)

interface DraftReports {
    fun observe(): Flow<List<DraftReport>>
    suspend fun get(id: String): DraftReport?
    suspend fun save(input: ReportInput, state: DraftState)
    suspend fun delete(id: String)
}
