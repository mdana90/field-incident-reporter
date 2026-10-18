package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.data.offline.DraftReport
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

internal fun testIncident(id: String = "incident-1") = Incident(
    id = id,
    title = "Pipe",
    description = "Leaking",
    severity = Severity.HIGH,
    location = "Building A",
    status = Status.OPEN,
    photos = emptyList(),
    createdAt = "2026-10-18T00:00:00.000Z",
    updatedAt = "2026-10-18T00:00:00.000Z",
)

internal class FakeIncidentRepository : IncidentRepository {
    val creates = mutableListOf<ReportInput>()
    val updates = mutableListOf<ReportInput>()
    var gets = 0
    var listResult: suspend () -> List<Incident> = { listOf(testIncident()) }
    var createResult: suspend (ReportInput) -> Incident = { testIncident() }
    var updateResult: suspend (ReportInput) -> Incident = { testIncident() }

    override suspend fun list() = resultOf { listResult() }
    override suspend fun get(id: String): Result<Incident> {
        gets++
        return Result.Success(testIncident(id))
    }
    override suspend fun create(input: ReportInput): Result<Incident> {
        creates += input
        return resultOf { createResult(input) }
    }
    override suspend fun update(id: String, input: ReportInput): Result<Incident> {
        updates += input
        return resultOf { updateResult(input) }
    }
    override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
}

internal class FakeDraftReports : DraftReports {
    private val rows = linkedMapOf<String, DraftReport>()
    private val stream = MutableStateFlow<List<DraftReport>>(emptyList())
    val saves = mutableListOf<Pair<ReportInput, DraftState>>()
    val deletes = mutableListOf<String>()
    var saveFailure: Exception? = null
    var getFailure: Exception? = null
    var observeFailure: Exception? = null

    fun stored(id: String): DraftReport? = rows[id]

    override fun observe(): Flow<List<DraftReport>> = observeFailure?.let { failure -> flow { throw failure } } ?: stream
    override suspend fun get(id: String): Result<DraftReport?> = resultOf {
        getFailure?.let { throw it }
        rows[id]
    }
    override suspend fun save(input: ReportInput, state: DraftState): Result<Unit> = resultOf {
        saveFailure?.let { throw it }
        val id = requireNotNull(input.clientRequestId)
        saves += input to state
        rows[id] = DraftReport(id, input, state, rows[id]?.createdAt ?: 1L)
        stream.value = rows.values.toList()
    }
    override suspend fun delete(id: String): Result<Unit> = resultOf {
        deletes += id
        rows.remove(id)
        stream.value = rows.values.toList()
    }
}
