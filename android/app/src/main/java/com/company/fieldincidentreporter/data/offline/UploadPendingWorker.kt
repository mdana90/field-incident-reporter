package com.company.fieldincidentreporter.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.fieldincidentreporter.data.IncidentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

enum class UploadFailureAction { RETRY, NEEDS_ATTENTION }

fun uploadFailureAction(failure: Throwable): UploadFailureAction = when (failure) {
    is IOException -> UploadFailureAction.RETRY
    is HttpException -> if (failure.code() >= 500) UploadFailureAction.RETRY else UploadFailureAction.NEEDS_ATTENTION
    else -> UploadFailureAction.NEEDS_ATTENTION
}

internal enum class PendingUploadResult { SUCCESS, RETRY }

internal suspend fun uploadPendingReport(
    id: String,
    store: PendingReports,
    repository: IncidentRepository,
): PendingUploadResult {
    val input = store.getInput(id) ?: return PendingUploadResult.SUCCESS
    store.markSending(id)
    return try {
        if (input.newPhotos.any { !it.exists() }) {
            store.mark(id, DeliveryState.NEEDS_ATTENTION)
            PendingUploadResult.SUCCESS
        } else {
            repository.create(input)
            store.complete(id)
            PendingUploadResult.SUCCESS
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        when (uploadFailureAction(failure)) {
            UploadFailureAction.RETRY -> {
                store.mark(id, DeliveryState.WAITING)
                PendingUploadResult.RETRY
            }
            UploadFailureAction.NEEDS_ATTENTION -> {
                store.mark(id, DeliveryState.NEEDS_ATTENTION)
                PendingUploadResult.SUCCESS
            }
        }
    }
}

@HiltWorker
class UploadPendingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val store: PendingReports,
    private val repository: IncidentRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(REPORT_ID) ?: return Result.failure()
        return when (uploadPendingReport(id, store, repository)) {
            PendingUploadResult.SUCCESS -> Result.success()
            PendingUploadResult.RETRY -> Result.retry()
        }
    }

    companion object { const val REPORT_ID = "report_id" }
}
