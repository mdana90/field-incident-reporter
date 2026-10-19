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
    // Use the locale's short date format; this does not read the device's custom date-format preference.
    return DateFormat.getDateInstance(DateFormat.SHORT, locale).apply { this.timeZone = timeZone }.format(parsed)
}
