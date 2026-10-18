package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftReportDao
import com.company.fieldincidentreporter.data.offline.DraftReportEntity
import com.company.fieldincidentreporter.data.offline.DraftReportStore
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftReportStoreTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun savesOnlySelectedPhotoNamesAndDeletesRowAndFolder() = runTest {
        val root = files.newFolder("drafts")
        val folder = root.resolve("request-id").apply { mkdirs() }
        val selected = folder.resolve("selected.jpg").apply { writeBytes(byteArrayOf(-1, -40)) }
        val unselected = folder.resolve("unselected.jpg").apply { writeBytes(byteArrayOf(-1, -40)) }
        val dao = FakeDao()
        val store = DraftReportStore(root, dao, Json)
        val input = ReportInput("Pipe", "Leaking", Severity.HIGH, "Building A", listOf(selected), clientRequestId = "request-id")

        store.save(input, DraftState.DRAFT)
        val createdAt = store.get("request-id")!!.createdAt
        store.save(input, DraftState.UNCONFIRMED)

        assertEquals(listOf(selected), store.get("request-id")!!.input.newPhotos)
        assertEquals(DraftState.UNCONFIRMED, store.get("request-id")!!.state)
        assertEquals(createdAt, store.get("request-id")!!.createdAt)
        assertTrue(unselected.exists())
        store.delete("request-id")
        assertEquals(null, store.get("request-id"))
        assertFalse(folder.exists())
    }

    private class FakeDao : DraftReportDao {
        private val rows = linkedMapOf<String, DraftReportEntity>()
        private val flow = MutableStateFlow<List<DraftReportEntity>>(emptyList())
        override fun observeAll(): Flow<List<DraftReportEntity>> = flow
        override suspend fun get(id: String): DraftReportEntity? = rows[id]
        override suspend fun upsert(report: DraftReportEntity) {
            rows[report.id] = report
            flow.value = rows.values.toList()
        }
        override suspend fun delete(id: String) {
            rows.remove(id)
            flow.value = rows.values.toList()
        }
    }
}
