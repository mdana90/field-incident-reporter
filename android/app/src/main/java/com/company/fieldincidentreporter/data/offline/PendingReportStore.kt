package com.company.fieldincidentreporter.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.model.Severity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class DeliveryState { WAITING, SENDING, NEEDS_ATTENTION }

data class PendingReport(
    val id: String,
    val title: String,
    val severity: Severity,
    val location: String,
    val state: DeliveryState,
    val attempted: Boolean,
    val createdAt: Long,
)

@Singleton
class PendingReportStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: PendingReportDao,
    private val json: Json,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun observe(): Flow<List<PendingReport>> = dao.observeAll().map { reports ->
        reports.map { entity ->
            PendingReport(
                id = entity.id,
                title = entity.title,
                severity = Severity.valueOf(entity.severity),
                location = entity.location,
                state = DeliveryState.valueOf(entity.state),
                attempted = entity.attempted,
                createdAt = entity.createdAt,
            )
        }
    }

    suspend fun queue(input: ReportInput) {
        val id = requireNotNull(input.clientRequestId)
        if (dao.get(id) != null) {
            schedule(id, replace = false)
            return
        }
        val directory = File(context.filesDir, "pending/$id")
        val copied = try {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                input.newPhotos.map { source ->
                    File(directory, "${UUID.randomUUID()}.${source.extension}").also { source.copyTo(it) }.absolutePath
                }
            }
        } catch (failure: Exception) {
            withContext(Dispatchers.IO) { directory.deleteRecursively() }
            throw failure
        }
        dao.upsert(PendingReportEntity(
            id = id,
            title = input.title,
            description = input.description,
            severity = input.severity.name,
            location = input.location,
            photoPathsJson = json.encodeToString(copied),
            state = DeliveryState.WAITING.name,
            attempted = true,
            createdAt = System.currentTimeMillis(),
        ))
        schedule(id, replace = false)
    }

    suspend fun get(id: String): PendingReportEntity? = dao.get(id)

    fun toInput(entity: PendingReportEntity): ReportInput = ReportInput(
        title = entity.title,
        description = entity.description,
        severity = Severity.valueOf(entity.severity),
        location = entity.location,
        newPhotos = json.decodeFromString<List<String>>(entity.photoPathsJson).map(::File),
        clientRequestId = entity.id,
    )

    suspend fun mark(id: String, state: DeliveryState) = dao.updateState(id, state.name)

    suspend fun complete(id: String) {
        dao.delete(id)
        withContext(Dispatchers.IO) { File(context.filesDir, "pending/$id").deleteRecursively() }
    }

    suspend fun discard(id: String) {
        val item = dao.get(id) ?: return
        if (!item.attempted || item.state == DeliveryState.NEEDS_ATTENTION.name) {
            workManager.cancelUniqueWork(workName(id))
            complete(id)
        }
    }

    suspend fun retry(id: String) {
        if (dao.get(id) == null) return
        dao.updateState(id, DeliveryState.WAITING.name)
        schedule(id, replace = true)
    }

    fun ensureScheduled(reports: List<PendingReport>) {
        reports.filter { it.state == DeliveryState.WAITING }.forEach { schedule(it.id, replace = false) }
    }

    private fun schedule(id: String, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<UploadPendingWorker>()
            .setInputData(workDataOf(UploadPendingWorker.REPORT_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("incident-upload")
            .build()
        workManager.enqueueUniqueWork(workName(id), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    private fun workName(id: String) = "upload-$id"
}
