package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftReport
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.incidentLocalDate
import com.company.fieldincidentreporter.ui.list.IncidentListViewModel
import com.company.fieldincidentreporter.ui.list.ListUiState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    // A separate dispatcher on the same scheduler stands in for Dispatchers.IO, so tests can tell where work ran.
    private val io = StandardTestDispatcher(dispatcher.scheduler)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun draftsRemainVisibleWhenServerListFails() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        val repository = FakeIncidentRepository().apply { listResult = { throw IOException("offline") } }
        val viewModel = IncidentListViewModel(repository, drafts, io)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun draftLoadFailureShowsDraftErrorAndKeepsServerList() = runTest(dispatcher) {
        val drafts = FakeDraftReports().apply { observeFailure = IOException("database unavailable") }
        val viewModel = IncidentListViewModel(FakeIncidentRepository(), drafts, io)
        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.draftError)
        assertEquals("incident-1", viewModel.state.value.incidents.single().incident.id)
    }

    @Test fun pullRefreshKeepsVisibleReportsAndNeverSendsDrafts() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        val repository = FakeIncidentRepository()
        val viewModel = IncidentListViewModel(repository, drafts, io)
        viewModel.refresh()
        advanceUntilIdle()

        val finishRefresh = CompletableDeferred<Unit>()
        repository.listResult = { finishRefresh.await(); throw IOException("offline") }
        viewModel.refresh(fromPull = true)
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertEquals("incident-1", viewModel.state.value.incidents.single().incident.id)
        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
        assertTrue(repository.creates.isEmpty())

        finishRefresh.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.refreshing)
        assertNotNull(viewModel.state.value.error)
        assertEquals("incident-1", viewModel.state.value.incidents.single().incident.id)
        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
    }

    @Test fun dataCallsRunOnIoDispatcher() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        drafts.dispatchers.clear()
        val repository = FakeIncidentRepository()
        val viewModel = IncidentListViewModel(repository, drafts, io)

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.deleteDraft("draft-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.drafts.isEmpty())
        assertEquals(setOf(io), repository.dispatchers)
        assertEquals(setOf(io), drafts.dispatchers)
    }

    @Test fun draftDeleteFailureKeepsDraftAndShowsError() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        drafts.deleteFailure = IOException("database unavailable")
        val viewModel = IncidentListViewModel(FakeIncidentRepository(), drafts, io)
        advanceUntilIdle()

        viewModel.deleteDraft("draft-1")
        advanceUntilIdle()

        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
        assertEquals("Could not delete this draft. Try again.", viewModel.state.value.draftError)
    }

    @Test fun loadedIncidentsCarryTheirFormattedDate() = runTest(dispatcher) {
        val viewModel = IncidentListViewModel(FakeIncidentRepository(), FakeDraftReports(), io)
        viewModel.refresh()
        advanceUntilIdle()

        val item = viewModel.state.value.incidents.single()
        assertEquals(incidentLocalDate(testIncident().createdAt), item.reportedOn)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test fun isEmptyOnlyWithoutIncidentsAndDrafts() = runTest(dispatcher) {
        val draft = DraftReport(
            "draft-1", ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT, 1L,
        )
        assertTrue(ListUiState().isEmpty)
        assertFalse(ListUiState(drafts = listOf(draft)).isEmpty)

        val viewModel = IncidentListViewModel(FakeIncidentRepository().apply { listResult = { emptyList() } }, FakeDraftReports(), io)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isEmpty)
    }
}
