package com.company.fieldincidentreporter.ui

import java.io.IOException
import retrofit2.HttpException

fun Throwable.userMessage(): String = when (this) {
    is IOException -> if (message?.startsWith("Photo") == true || message?.startsWith("Could not read") == true) {
        message.orEmpty()
    } else "Could not reach the server. Check the connection and try again."
    is HttpException -> when (code()) {
        400 -> "Please check the report details and photos."
        404 -> "This report could not be found."
        413 -> "A photo is too large. Choose one under 10 MiB."
        else -> "Server error (${code()}). Try again."
    }
    else -> message ?: "Something went wrong. Try again."
}
