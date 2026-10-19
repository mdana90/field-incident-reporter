package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.ui.incidentLocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentDateTest {
    @Test fun formatsUtcTimestampAsShortLocalDate() {
        assertEquals("10/18/26", incidentLocalDate("2026-10-18T09:30:00.000Z", Locale.US, utc))
    }

    @Test fun convertsToTheDeviceTimeZone() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals("10/17/26", incidentLocalDate("2026-10-18T02:00:00.000Z", Locale.US, losAngeles))
    }

    @Test fun followsTheLocaleDateOrder() {
        assertEquals("18/10/2026", incidentLocalDate("2026-10-18T09:30:00.000Z", Locale.UK, utc))
    }

    @Test fun unparseableTimestampFallsBackToItsDatePart() {
        assertEquals("2026-10-18", incidentLocalDate("2026-10-18 09:30", Locale.US, utc))
    }

    private val utc = TimeZone.getTimeZone("UTC")
}
