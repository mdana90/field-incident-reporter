package com.company.fieldincidentreporter.ui

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Formats the server's UTC timestamp as a short local date, or falls back to its date part if it cannot be parsed. */
fun incidentLocalDate(
    isoUtc: String,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            this.timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(isoUtc)
    }.getOrNull() ?: return isoUtc.take(10)
    // Same format as android.text.format.DateFormat.getDateFormat(context), without needing a Context.
    return DateFormat.getDateInstance(DateFormat.SHORT, locale).apply { this.timeZone = timeZone }.format(parsed)
}
