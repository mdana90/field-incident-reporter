package com.company.fieldincidentreporter.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.fieldincidentreporter.data.IncidentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import retrofit2.HttpException

enum class UploadFailureAction { RETRY, NEEDS_ATTENTION }

fun uploadFailureAction(failure: Throwable): UploadFailureAction = when (failure) {
    is IOException -> UploadFailureAction.RETRY
    is HttpException -> if (failure.code() >= 500) UploadFailureAction.RETRY else UploadFailureAction.NEEDS_ATTENTION
    else -> UploadFailureAction.NEEDS_ATTENTION
}

@HiltWorker
class UploadPendingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val store: PendingReportStore,
    private val repository: IncidentRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(REPORT_ID) ?: return Result.failure()
        val entity = store.get(id) ?: return Result.success()
        store.mark(id, DeliveryState.SENDING)
        return try {
            val input = store.toInput(entity)
            if (input.newPhotos.any { !it.exists() }) {
                store.mark(id, DeliveryState.NEEDS_ATTENTION)
                Result.success()
            } else {
                repository.create(input)
                store.complete(id)
                Result.success()
            }
        } catch (failure: Exception) {
            when (uploadFailureAction(failure)) {
                UploadFailureAction.RETRY -> {
                    store.mark(id, DeliveryState.WAITING)
                    Result.retry()
                }
                UploadFailureAction.NEEDS_ATTENTION -> {
                    store.mark(id, DeliveryState.NEEDS_ATTENTION)
                    Result.success()
                }
            }
        }
    }

    companion object { const val REPORT_ID = "report_id" }
}
