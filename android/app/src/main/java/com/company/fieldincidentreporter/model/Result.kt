package com.company.fieldincidentreporter.model

/**
 * Represents a result of a data operation.
 *
 * @param T The type of the data that is returned in case of success.
 */
sealed class Result<out T> {
    /**
     * Represents a successful result of an operation.
     *
     * @property data The data that is returned in case of success.
     */
    class Success<out T>(val data: T) : Result<T>()

    /**
     * Represents an error result of an operation.
     *
     * @property exception The exception that caused the error.
     * @property message The message that describes the error to the user.
     */
    class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "An unknown error occurred",
    ) : Result<Nothing>()
}
