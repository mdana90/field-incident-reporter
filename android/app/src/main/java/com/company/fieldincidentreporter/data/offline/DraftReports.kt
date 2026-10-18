package com.company.fieldincidentreporter.data.offline

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.model.Result
import kotlinx.coroutines.flow.Flow

enum class DraftState { DRAFT, UNCONFIRMED }

data class DraftReport(
    val id: String,
    val input: ReportInput,
    val state: DraftState,
    val createdAt: Long,
)

interface DraftReports {
    fun observe(): Flow<Result<List<DraftReport>>>
    suspend fun get(id: String): Result<DraftReport?>
    suspend fun save(input: ReportInput, state: DraftState): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
