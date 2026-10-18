package com.company.fieldincidentreporter.ui

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun incidentLocalDate(isoUtc: String, context: Context): String {
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(isoUtc)
    }.getOrNull() ?: return isoUtc.take(10)
    return DateFormat.getDateFormat(context).format(parsed)
}
