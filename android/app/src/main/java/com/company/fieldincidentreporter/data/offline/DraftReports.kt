package com.company.fieldincidentreporter.data.offline

import com.company.fieldincidentreporter.model.DraftReport
import com.company.fieldincidentreporter.model.DraftState
import com.company.fieldincidentreporter.model.ReportInput
import com.company.fieldincidentreporter.model.Result
import kotlinx.coroutines.flow.Flow

interface DraftReports {
    fun observe(): Flow<List<DraftReport>>
    suspend fun get(id: String): Result<DraftReport?>
    suspend fun save(input: ReportInput, state: DraftState): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
