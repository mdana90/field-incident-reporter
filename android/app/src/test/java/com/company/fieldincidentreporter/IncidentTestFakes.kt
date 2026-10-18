package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftReport
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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

    override suspend fun list() = listResult()
    override suspend fun get(id: String): Incident {
        gets++
        return testIncident(id)
    }
    override suspend fun create(input: ReportInput): Incident {
        creates += input
        return createResult(input)
    }
    override suspend fun update(id: String, input: ReportInput): Incident {
        updates += input
        return updateResult(input)
    }
    override suspend fun delete(id: String) = Unit
}

internal class FakeDraftReports : DraftReports {
    private val rows = linkedMapOf<String, DraftReport>()
    private val flow = MutableStateFlow<List<DraftReport>>(emptyList())
    val saves = mutableListOf<Pair<ReportInput, DraftState>>()
    val deletes = mutableListOf<String>()
    var saveFailure: Exception? = null

    override fun observe(): Flow<List<DraftReport>> = flow
    override suspend fun get(id: String): DraftReport? = rows[id]
    override suspend fun save(input: ReportInput, state: DraftState) {
        saveFailure?.let { throw it }
        val id = requireNotNull(input.clientRequestId)
        saves += input to state
        rows[id] = DraftReport(id, input, state, rows[id]?.createdAt ?: 1L)
        flow.value = rows.values.toList()
    }
    override suspend fun delete(id: String) {
        deletes += id
        rows.remove(id)
        flow.value = rows.values.toList()
    }
}
