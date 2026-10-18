package com.company.fieldincidentreporter

import androidx.lifecycle.SavedStateHandle
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftState
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

    @Test fun invalidFormBlocksSaveAndSend() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports()
        val form = viewModel(SavedStateHandle(mapOf("id" to "")), repository, drafts)

        form.saveDraft()
        form.sendReport()
        advanceUntilIdle()

        assertTrue(drafts.saves.isEmpty())
        assertTrue(repository.creates.isEmpty())
        assertTrue(form.state.value.fieldErrors.containsKey("photos"))
        assertTrue(form.state.value.fieldErrors.containsKey("title"))
    }

    @Test fun savedDraftReloadsOnlySelectedPhotosAndDiscardKeepsSavedFile() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        val saved = photo("saved.jpg")
        val removed = photo("removed.jpg")
        drafts.save(input(listOf(saved)), DraftState.DRAFT)
        val form = viewModel(SavedStateHandle(mapOf("draftId" to "stable-request-id")), drafts = drafts)
        advanceUntilIdle()
        assertEquals(listOf(saved), form.state.value.photos.mapNotNull { it.localFile })

        form.removePhoto(saved.absolutePath)
        form.cameraResult(removed.absolutePath, true)
        advanceUntilIdle()
        form.discardChanges()

        assertTrue(saved.exists())
        assertFalse(removed.exists())
        assertEquals(listOf(saved), drafts.stored("stable-request-id")?.input?.newPhotos)
    }

    @Test fun saveDraftPersistsValidFormWithoutSending() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()

        form.saveDraft()
        advanceUntilIdle()

        assertEquals(DraftState.DRAFT, drafts.stored("stable-request-id")?.state)
        assertTrue(repository.creates.isEmpty())
        assertTrue(form.state.value.draftSaved)
        assertTrue(photoStore.deletedDrafts.isEmpty())
    }

    @Test fun replacingSavedPhotoKeepsOriginalUntilSaveCompletes() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        val original = photo("original.jpg")
        val replacement = photo("replacement.jpg")
        drafts.save(input(listOf(original)), DraftState.DRAFT)
        val form = viewModel(SavedStateHandle(mapOf("draftId" to "stable-request-id")), drafts = drafts)
        advanceUntilIdle()

        form.removePhoto(original.absolutePath)
        form.cameraResult(replacement.absolutePath, true)
        advanceUntilIdle()
        assertTrue(original.exists())

        form.saveDraft()
        advanceUntilIdle()
        assertFalse(original.exists())
        assertEquals(listOf(replacement), drafts.stored("stable-request-id")!!.input.newPhotos)
    }

    @Test fun repeatSendWhileWaitingSendsOnlyOnceAndSuccessDeletesDraft() = runTest(dispatcher) {
        val response = CompletableDeferred<com.company.fieldincidentreporter.model.Incident>()
        val repository = FakeIncidentRepository().apply { createResult = { response.await() } }
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()

        form.sendReport()
        runCurrent()
        form.sendReport()
        runCurrent()
        assertEquals(1, repository.creates.size)
        assertEquals(DraftState.UNCONFIRMED, drafts.stored("stable-request-id")?.state)
        response.complete(testIncident())
        advanceUntilIdle()

        assertEquals("incident-1", form.state.value.savedId)
        assertEquals(listOf("stable-request-id"), drafts.deletes)
        assertEquals(null, drafts.stored("stable-request-id"))
    }

    @Test fun inFlightSendRestoresAsLockedFromRoomEvenWithStaleSavedState() = runTest(dispatcher) {
        val response = CompletableDeferred<com.company.fieldincidentreporter.model.Incident>()
        val repository = FakeIncidentRepository().apply { createResult = { response.await() } }
        val drafts = FakeDraftReports()
        val handle = draftHandle()
        val first = viewModel(handle, repository, drafts)
        runCurrent()

        first.sendReport()
        runCurrent()
        assertEquals(DraftState.UNCONFIRMED, drafts.stored("stable-request-id")?.state)
        assertEquals(true, handle.get<Boolean>("draftStored"))

        val staleHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        staleHandle["draftStored"] = false
        val restored = viewModel(staleHandle, repository, drafts)
        runCurrent()
        assertTrue(restored.state.value.unconfirmed)
        assertFalse(restored.state.value.loading)

        restored.title("Changed after restart")
        restored.saveDraft()
        runCurrent()
        assertEquals("Pipe", restored.state.value.title)
        assertEquals(DraftState.UNCONFIRMED, drafts.stored("stable-request-id")?.state)
        assertEquals(1, drafts.saves.size)

        response.complete(testIncident())
        advanceUntilIdle()
    }

    @Test fun connectionAndServerFailuresKeepUnconfirmedDraft() = runTest(dispatcher) {
        for (failure in listOf(IOException("offline"), httpFailure(503))) {
            val repository = FakeIncidentRepository().apply { createResult = { throw failure } }
            val drafts = FakeDraftReports()
            val form = viewModel(draftHandle(), repository, drafts)
            runCurrent()

            form.sendReport()
            advanceUntilIdle()

            assertTrue(form.state.value.sendUnconfirmed)
            assertEquals(DraftState.UNCONFIRMED, drafts.stored("stable-request-id")?.state)
            assertTrue(drafts.deletes.isEmpty())
            assertTrue(photoStore.deletedDrafts.isEmpty())
        }
    }

    @Test fun retryUsesIdenticalPayloadAndRequestId() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        val payload = input(listOf(photo("retry.jpg")))
        drafts.save(payload, DraftState.UNCONFIRMED)
        val repository = FakeIncidentRepository()
        val form = viewModel(SavedStateHandle(mapOf("draftId" to "stable-request-id")), repository, drafts)
        advanceUntilIdle()

        form.title("Unsent edit")
        form.removePhoto(payload.newPhotos.single().absolutePath)
        form.sendReport()
        advanceUntilIdle()

        assertEquals(payload, repository.creates.single())
        assertEquals("Pipe", repository.creates.single().title)
        assertEquals(listOf("stable-request-id"), drafts.deletes)
    }

    @Test fun validationRejectionReturnsToEditableDraft() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { createResult = { throw httpFailure(400) } }
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()

        form.sendReport()
        advanceUntilIdle()

        assertEquals(DraftState.DRAFT, drafts.stored("stable-request-id")?.state)
        assertFalse(form.state.value.unconfirmed)
        assertTrue(form.state.value.error?.contains("check") == true)
        assertTrue(drafts.deletes.isEmpty())
    }

    @Test fun missingPhotoBlocksSendWithActionableMessage() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()
        val missing = form.state.value.photos.single().localFile!!
        missing.delete()

        form.sendReport()
        advanceUntilIdle()

        assertTrue(repository.creates.isEmpty())
        assertTrue(drafts.saves.isEmpty())
        assertTrue(form.state.value.error?.contains("photo") == true)
    }

    @Test fun lockedDraftWithMissingPhotoExplainsRecovery() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        val missing = photo("missing-locked.jpg")
        drafts.save(input(listOf(missing)), DraftState.UNCONFIRMED)
        missing.delete()
        val repository = FakeIncidentRepository()
        val form = viewModel(SavedStateHandle(mapOf("draftId" to "stable-request-id")), repository, drafts)
        advanceUntilIdle()

        form.sendReport()
        advanceUntilIdle()

        assertTrue(repository.creates.isEmpty())
        assertEquals(DraftState.UNCONFIRMED, drafts.stored("stable-request-id")?.state)
        assertTrue(form.state.value.error?.contains("Delete the draft and report again") == true)
    }

    @Test fun backAfterDraftLoadFailureDoesNotDeleteItsPhotos() = runTest(dispatcher) {
        val photo = photo("saved-during-load-error.jpg")
        photoStore.onDeleteDraft = { photo.delete() }
        val drafts = FakeDraftReports().apply { getFailure = IOException("database unavailable") }
        val form = viewModel(SavedStateHandle(mapOf("draftId" to "stable-request-id")), drafts = drafts)
        form.discardChanges()
        assertTrue(photo.exists())
        assertTrue(photoStore.deletedDrafts.isEmpty())
        advanceUntilIdle()
        assertTrue(form.state.value.loadError)

        form.discardChanges()

        assertTrue(photo.exists())
        assertTrue(photoStore.deletedDrafts.isEmpty())
    }

    @Test fun localWriteFailureBlocksSend() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports().apply { saveFailure = IOException("disk full") }
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()

        form.sendReport()
        advanceUntilIdle()

        assertTrue(repository.creates.isEmpty())
        assertTrue(form.state.value.error?.contains("device") == true)
    }

    @Test fun failedSubmittedIncidentEditStaysOnline() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { updateResult = { throw IOException("offline") } }
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle("existing"), repository, drafts)

        form.sendReport()
        advanceUntilIdle()

        assertEquals(1, repository.updates.size)
        assertEquals(listOf("kept-photo"), repository.updates.single().keepPhotoIds)
        assertTrue(drafts.saves.isEmpty())
    }

    @Test fun cancelledCameraCaptureDeletesItsFile() = runTest(dispatcher) {
        val photo = photo("cancelled.jpg")
        val form = viewModel(SavedStateHandle(mapOf("id" to "")))
        form.cameraResult(photo.absolutePath, false)
        assertFalse(photo.exists())
    }

    private fun viewModel(
        handle: SavedStateHandle,
        repository: FakeIncidentRepository = FakeIncidentRepository(),
        drafts: FakeDraftReports = FakeDraftReports(),
    ) = IncidentFormViewModel(repository, photoStore, drafts, handle)

    private fun photo(name: String) = files.newFile(name).also { it.writeBytes(byteArrayOf(-1, -40, -1, -39)) }

    private fun input(photos: List<java.io.File>) = ReportInput(
        title = "Pipe", description = "Leaking", severity = Severity.HIGH,
        location = "Building A", newPhotos = photos, clientRequestId = "stable-request-id",
    )

    private fun draftHandle(editId: String = ""): SavedStateHandle {
        val photo = photo("photo-${files.root.listFiles()?.size}.jpg")
        val photos = mutableListOf(mapOf("path" to photo.absolutePath))
        if (editId.isNotBlank()) photos += mapOf("id" to "kept-photo", "url" to "/uploads/kept-photo.jpg")
        return SavedStateHandle(mapOf(
            "id" to editId, "clientRequestId" to "stable-request-id",
            "draftExists" to true, "draftTitle" to "Pipe", "draftDescription" to "Leaking",
            "draftSeverity" to "HIGH", "draftLocation" to "Building A",
            "draftPhotos" to Json.encodeToString(photos), "draftDirty" to true,
        ))
    }

    private fun httpFailure(code: Int) = HttpException(Response.error<Any>(code, "failure".toResponseBody()))
}
