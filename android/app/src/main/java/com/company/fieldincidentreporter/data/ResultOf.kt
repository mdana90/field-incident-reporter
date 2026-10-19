package com.company.fieldincidentreporter.data

import com.company.fieldincidentreporter.model.Result
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import timber.log.Timber

/**
 * Runs a data operation and turns ordinary exceptions into [Result.Error].
 * Cancellation is rethrown so a cancelled coroutine stops instead of reporting an error.
 */
suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Exception) {
    Timber.w(failure, "Data operation failed")
    Result.Error(failure, failure.userMessage())
}

fun Throwable.userMessage(): String = when (this) {
    is PhotoException -> message.orEmpty()
    is IOException -> "Could not reach the server. Check the connection and try again."
    is HttpException -> when (code()) {
        400, 422 -> "Please check the report details and photos."
        404 -> "This report could not be found."
        413 -> "A photo is too large. Choose one under 10 MiB."
        else -> "Server error (${code()}). Try again."
    }
    else -> "Something went wrong. Try again."
}

/** True for validation responses that require changing the report before retrying. */
fun Throwable.isRejection(): Boolean = this is HttpException && code() in listOf(400, 413, 422)
