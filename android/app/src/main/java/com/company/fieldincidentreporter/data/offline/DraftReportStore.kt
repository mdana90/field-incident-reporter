package com.company.fieldincidentreporter.data.offline

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.model.Severity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DraftReportStore(
    private val draftsRoot: File,
    private val dao: DraftReportDao,
    private val json: Json,
) : DraftReports {
    override fun observe(): Flow<List<DraftReport>> = dao.observeAll().map { rows -> rows.map(::toDraft) }

    override suspend fun get(id: String): Result<DraftReport?> = resultOf { dao.get(id)?.let(::toDraft) }

    override suspend fun save(input: ReportInput, state: DraftState): Result<Unit> = resultOf {
        val id = requireNotNull(input.clientRequestId)
        val directory = draftDirectory(id)
        require(input.newPhotos.all { it.parentFile == directory && it.name.endsWith(".jpg") })
        dao.upsert(DraftReportEntity(
            id = id,
            title = input.title,
            description = input.description,
            severity = input.severity.name,
            location = input.location,
            photoNamesJson = json.encodeToString(input.newPhotos.map(File::getName)),
            state = state.name,
            createdAt = dao.get(id)?.createdAt ?: System.currentTimeMillis(),
        ))
    }

    override suspend fun delete(id: String): Result<Unit> = resultOf {
        dao.delete(id)
        withContext(Dispatchers.IO) { draftDirectory(id).deleteRecursively() }
    }

    private fun toDraft(row: DraftReportEntity): DraftReport = DraftReport(
        id = row.id,
        input = ReportInput(
            title = row.title,
            description = row.description,
            severity = Severity.valueOf(row.severity),
            location = row.location,
            newPhotos = json.decodeFromString<List<String>>(row.photoNamesJson).map { File(draftDirectory(row.id), it) },
            clientRequestId = row.id,
        ),
        state = DraftState.valueOf(row.state),
        createdAt = row.createdAt,
    )

    private fun draftDirectory(id: String) = File(draftsRoot, id)
}
