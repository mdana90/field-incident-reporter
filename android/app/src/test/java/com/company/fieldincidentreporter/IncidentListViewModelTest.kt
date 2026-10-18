package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.list.IncidentListViewModel
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

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun draftsRemainVisibleWhenServerListFails() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        val repository = FakeIncidentRepository().apply { listResult = { throw IOException("offline") } }
        val viewModel = IncidentListViewModel(repository, drafts)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun draftLoadFailureShowsDraftErrorAndKeepsServerList() = runTest(dispatcher) {
        val drafts = FakeDraftReports().apply { observeFailure = IOException("database unavailable") }
        val viewModel = IncidentListViewModel(FakeIncidentRepository(), drafts)
        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.draftError)
        assertEquals("incident-1", viewModel.state.value.incidents.single().id)
    }

    @Test fun pullRefreshKeepsVisibleReportsAndNeverSendsDrafts() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(ReportInput("Pipe", "Leaking", Severity.HIGH, "A", emptyList(), clientRequestId = "draft-1"), DraftState.DRAFT)
        val repository = FakeIncidentRepository()
        val viewModel = IncidentListViewModel(repository, drafts)
        viewModel.refresh()
        advanceUntilIdle()

        val finishRefresh = CompletableDeferred<Unit>()
        repository.listResult = { finishRefresh.await(); throw IOException("offline") }
        viewModel.refresh(fromPull = true)
        runCurrent()

        assertTrue(viewModel.state.value.refreshing)
        assertEquals("incident-1", viewModel.state.value.incidents.single().id)
        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
        assertTrue(repository.creates.isEmpty())

        finishRefresh.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.refreshing)
        assertNotNull(viewModel.state.value.error)
        assertEquals("incident-1", viewModel.state.value.incidents.single().id)
        assertEquals("draft-1", viewModel.state.value.drafts.single().id)
    }
}
