package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.form.validateReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportValidationTest {
    @Test fun missingFieldsAreIdentified() {
        val errors = validateReport(" ", "", null, "", 0)
        assertEquals(setOf("title", "description", "severity", "location", "photos"), errors.keys)
    }

    @Test fun validReportAndThreePhotosPass() {
        assertTrue(validateReport("Pipe", "Leaking", Severity.HIGH, "Building A", 3).isEmpty())
    }

    @Test fun fourPhotosFail() {
        assertEquals("Choose 1 to 3 photos", validateReport("Pipe", "Leaking", Severity.HIGH, "Building A", 4)["photos"])
    }
}
