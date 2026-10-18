package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.list.IncidentListViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
