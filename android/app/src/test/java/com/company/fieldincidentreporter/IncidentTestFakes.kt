package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DeliveryState
import com.company.fieldincidentreporter.data.offline.PendingReport
import com.company.fieldincidentreporter.data.offline.PendingReports
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    var createResult: suspend (ReportInput) -> Incident = { testIncident() }
    var updateResult: suspend (ReportInput) -> Incident = { testIncident() }

    override suspend fun list() = listOf(testIncident())
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

internal class FakePendingReports(var input: ReportInput? = null) : PendingReports {
    val queued = mutableListOf<ReportInput>()
    val states = mutableListOf<DeliveryState>()
    var completed = false
    var sending = false

    override fun observe(): Flow<List<PendingReport>> = flowOf(emptyList())
    override suspend fun queue(input: ReportInput) { queued += input }
    override suspend fun getInput(id: String): ReportInput? = input
    override suspend fun markSending(id: String) { sending = true }
    override suspend fun mark(id: String, state: DeliveryState) { states += state }
    override suspend fun complete(id: String) { completed = true }
    override suspend fun retry(id: String) = Unit
    override suspend fun discard(id: String) = Unit
    override fun ensureScheduled(reports: List<PendingReport>) = Unit
}
