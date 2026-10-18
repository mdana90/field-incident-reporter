package com.company.fieldincidentreporter

import androidx.lifecycle.SavedStateHandle
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentFormViewModelTest {
    @get:Rule val files = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val photoStore = FakeDraftPhotoStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun invalidFormDoesNotSubmit() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val viewModel = viewModel(SavedStateHandle(mapOf("id" to "")), repository)

        viewModel.save()
        advanceUntilIdle()

        assertTrue(repository.creates.isEmpty())
        assertTrue(viewModel.state.value.fieldErrors.containsKey("photos"))
        assertTrue(viewModel.state.value.fieldErrors.containsKey("title"))
    }

    @Test fun repeatSaveWhileWaitingSendsOnlyOnce() = runTest(dispatcher) {
        val response = CompletableDeferred<com.company.fieldincidentreporter.model.Incident>()
        val repository = FakeIncidentRepository().apply { createResult = { response.await() } }
        val viewModel = viewModel(draftHandle(), repository)

        viewModel.save()
        runCurrent()
        viewModel.save()
        runCurrent()

        assertEquals(1, repository.creates.size)
        response.complete(testIncident())
        advanceUntilIdle()
        assertEquals("incident-1", viewModel.state.value.savedId)
        assertEquals(listOf("stable-request-id"), photoStore.deletedDrafts)
    }

    @Test fun transientCreateFailureQueuesTheSameRequestId() = runTest(dispatcher) {
        for (failure in listOf(IOException("offline"), httpFailure(503))) {
            val repository = FakeIncidentRepository().apply { createResult = { throw failure } }
            val pending = FakePendingReports()
            val viewModel = viewModel(draftHandle(), repository, pending)

            viewModel.save()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.queued)
            assertEquals("stable-request-id", repository.creates.single().clientRequestId)
            assertEquals(repository.creates.single(), pending.queued.single())
        }
        assertEquals(2, photoStore.deletedDrafts.size)
    }

    @Test fun validationFailureKeepsFieldsAndDoesNotQueue() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { createResult = { throw httpFailure(400) } }
        val pending = FakePendingReports()
        val viewModel = viewModel(draftHandle(), repository, pending)

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.queued)
        assertEquals("Pipe", viewModel.state.value.title)
        assertEquals(Severity.HIGH, viewModel.state.value.severity)
        assertTrue(pending.queued.isEmpty())
        assertTrue(photoStore.deletedDrafts.isEmpty())
    }

    @Test fun failedEditKeepsDraftAndNeverQueues() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { updateResult = { throw IOException("offline") } }
        val pending = FakePendingReports()
        val viewModel = viewModel(draftHandle("existing"), repository, pending)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, repository.updates.size)
        assertEquals(listOf("kept-photo"), repository.updates.single().keepPhotoIds)
        assertEquals("Pipe", viewModel.state.value.title)
        assertTrue(pending.queued.isEmpty())
        assertTrue(photoStore.deletedDrafts.isEmpty())
    }

    @Test fun editDraftRestoresWithoutReloadingIncident() = runTest(dispatcher) {
        val handle = draftHandle("existing")
        val repository = FakeIncidentRepository()
        val first = viewModel(handle, repository)
        first.title("Changed pipe")
        first.location("Roof")
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        val restored = viewModel(restoredHandle, repository)
        advanceUntilIdle()

        assertEquals("Changed pipe", restored.state.value.title)
        assertEquals("Roof", restored.state.value.location)
        assertEquals(2, restored.state.value.photos.size)
        assertEquals("kept-photo", restored.state.value.photos[1].id)
        assertEquals("/uploads/kept-photo.jpg", restored.state.value.photos[1].url)
        assertEquals(0, repository.gets)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        repository: FakeIncidentRepository = FakeIncidentRepository(),
        pending: FakePendingReports = FakePendingReports(),
    ) = IncidentFormViewModel(repository, photoStore, pending, handle)

    private fun draftHandle(editId: String = ""): SavedStateHandle {
        val photo = files.newFile("photo-${files.root.listFiles()?.size}.jpg")
        photo.writeBytes(byteArrayOf(-1, -40, -1, -39))
        val photos = mutableListOf(mapOf("path" to photo.absolutePath))
        if (editId.isNotBlank()) photos += mapOf("id" to "kept-photo", "url" to "/uploads/kept-photo.jpg")
        return SavedStateHandle(mapOf(
            "id" to editId,
            "clientRequestId" to "stable-request-id",
            "draftExists" to true,
            "draftTitle" to "Pipe",
            "draftDescription" to "Leaking",
            "draftSeverity" to "HIGH",
            "draftLocation" to "Building A",
            "draftPhotos" to Json.encodeToString(photos),
            "draftDirty" to true,
        ))
    }

    private fun httpFailure(code: Int) = HttpException(Response.error<Any>(code, "failure".toResponseBody()))
}
