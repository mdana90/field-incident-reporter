package com.company.fieldincidentreporter

import androidx.lifecycle.SavedStateHandle
import com.company.fieldincidentreporter.ui.detail.IncidentDetailViewModel
import com.company.fieldincidentreporter.ui.detail.IncidentDetailViewModel.Companion.KEY_ID
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    // A separate dispatcher on the same scheduler stands in for Dispatchers.IO, so tests can tell where work ran.
    private val io = StandardTestDispatcher(dispatcher.scheduler)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun refreshLoadsIncidentOnIoDispatcher() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val viewModel = viewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("incident-7", viewModel.state.value.incident?.id)
        assertFalse(viewModel.state.value.loading)
        assertEquals(setOf(io), repository.dispatchers)
    }

    @Test fun refreshFailureShowsError() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { getResult = { throw IOException("offline") } }
        val viewModel = viewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.state.value.incident)
        assertFalse(viewModel.state.value.loading)
        assertNotNull(viewModel.state.value.error)
    }

    @Test fun deleteRunsOnIoDispatcherAndMarksDeleted() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val viewModel = viewModel(repository)

        viewModel.delete()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.deleted)
        assertFalse(viewModel.state.value.deleting)
        assertEquals(setOf(io), repository.dispatchers)
    }

    @Test fun deleteFailureKeepsIncidentAndShowsError() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { deleteResult = { throw IOException("offline") } }
        val viewModel = viewModel(repository)

        viewModel.delete()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.deleted)
        assertFalse(viewModel.state.value.deleting)
        assertNotNull(viewModel.state.value.error)
    }

    private fun viewModel(repository: FakeIncidentRepository) =
        IncidentDetailViewModel(repository, io, SavedStateHandle(mapOf(KEY_ID to "incident-7")))
}
