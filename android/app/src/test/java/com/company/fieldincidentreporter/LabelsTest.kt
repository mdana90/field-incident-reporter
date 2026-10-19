package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status
import com.company.fieldincidentreporter.ui.label
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test fun severityLabelsAreSentenceCase() {
        assertEquals(listOf("Low", "Medium", "High", "Critical"), Severity.entries.map { it.label })
    }

    @Test fun statusLabelsReplaceUnderscoresWithSpaces() {
        assertEquals(listOf("Open", "In progress", "Resolved"), Status.entries.map { it.label })
    }
}
