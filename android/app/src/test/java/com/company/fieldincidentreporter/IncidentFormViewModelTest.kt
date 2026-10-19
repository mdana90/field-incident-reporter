package com.company.fieldincidentreporter

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.company.fieldincidentreporter.data.CameraPhoto
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel.Companion.KEY_CAMERA_PATH
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel.Companion.KEY_DRAFT_ID
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel.Companion.KEY_EDIT_ID
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel.Companion.KEY_FORM
import com.company.fieldincidentreporter.ui.form.IncidentFormViewModel.Companion.KEY_REQUEST_ID
import com.company.fieldincidentreporter.ui.form.SavedForm
import com.company.fieldincidentreporter.ui.form.SavedFormPhoto
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // A separate dispatcher on the same scheduler stands in for Dispatchers.IO, so tests can tell where work ran.
    private val io = StandardTestDispatcher(dispatcher.scheduler)
    private val photoStore = FakeDraftPhotoStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun invalidFormBlocksSaveAndSend() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports()
        val form = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "")), repository, drafts)

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
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id", KEY_CAMERA_PATH to removed.absolutePath)), drafts = drafts)
        advanceUntilIdle()
        assertEquals(listOf(saved), form.state.value.photos.mapNotNull { it.localFile })

        form.removePhoto(saved.absolutePath)
        form.cameraResult(true)
        advanceUntilIdle()
        form.discardChanges()
        advanceUntilIdle()

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
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id", KEY_CAMERA_PATH to replacement.absolutePath)), drafts = drafts)
        advanceUntilIdle()

        form.removePhoto(original.absolutePath)
        form.cameraResult(true)
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
        assertTrue(savedForm(handle).stored)

        val staleHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        staleHandle[KEY_FORM] = savedForm(handle).copy(stored = false)
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
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id")), repository, drafts)
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
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id")), repository, drafts)
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
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id")), drafts = drafts)
        form.discardChanges()
        assertTrue(photo.exists())
        assertTrue(photoStore.deletedDrafts.isEmpty())
        advanceUntilIdle()
        assertTrue(form.state.value.loadError)

        form.discardChanges()
        advanceUntilIdle()

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
        assertFalse("a failed save keeps the form open, not the load error screen", form.state.value.loadError)
        assertTrue(form.state.value.editable)
    }

    @Test fun cancelledCameraCaptureDeletesItsFile() = runTest(dispatcher) {
        val photo = photo("cancelled.jpg")
        val form = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "", KEY_CAMERA_PATH to photo.absolutePath)))
        form.cameraResult(false)
        advanceUntilIdle()
        assertFalse(photo.exists())
    }

    @Test fun cameraThatCannotOpenDeletesItsFileAndShowsError() = runTest(dispatcher) {
        val photo = photo("unopened.jpg")
        val form = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "", KEY_CAMERA_PATH to photo.absolutePath)))
        form.cameraLaunchFailed(IOException("no camera app"))
        advanceUntilIdle()
        assertFalse(photo.exists())
        assertEquals("Could not open the camera. Try again.", form.state.value.error)
    }

    @Test fun preparedCameraPhotoIsAddedWhenCaptureSucceeds() = runTest(dispatcher) {
        val captured = photo("captured.jpg")
        val uri = mockk<Uri>()
        photoStore.cameraPhoto = { CameraPhoto(uri, captured) }
        val handle = SavedStateHandle(mapOf(KEY_EDIT_ID to ""))
        val form = viewModel(handle)

        assertEquals(uri, form.prepareCameraPhoto())
        assertEquals(captured.absolutePath, handle.get<String>(KEY_CAMERA_PATH))

        form.cameraResult(true)
        advanceUntilIdle()
        assertEquals(listOf(captured), form.state.value.photos.mapNotNull { it.localFile })
        assertNull(handle.get<String>(KEY_CAMERA_PATH))
    }

    @Test fun cameraPhotoThatCannotBeCreatedShowsError() = runTest(dispatcher) {
        photoStore.cameraPhoto = { throw IOException("disk full") }
        val handle = SavedStateHandle(mapOf(KEY_EDIT_ID to ""))
        val form = viewModel(handle)

        assertNull(form.prepareCameraPhoto())
        assertEquals("Could not open the camera. Try again.", form.state.value.error)
        assertNull(handle.get<String>(KEY_CAMERA_PATH))
    }

    @Test fun cameraResultWithoutPreparedPhotoIsIgnored() = runTest(dispatcher) {
        val form = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "")))
        form.cameraResult(true)
        advanceUntilIdle()
        assertTrue(form.state.value.photos.isEmpty())
        assertNull(form.state.value.error)
    }

    @Test fun capturedPhotoBeyondLimitIsDeleted() = runTest(dispatcher) {
        val extra = photo("extra.jpg")
        val form = viewModel(draftHandle(photoCount = 3).apply { set(KEY_CAMERA_PATH, extra.absolutePath) })
        runCurrent()
        assertFalse(form.state.value.hasPhotoSlots)

        form.cameraResult(true)
        advanceUntilIdle()
        assertFalse(extra.exists())
        assertEquals(3, form.state.value.photos.size)
    }

    @Test fun pickedPhotosOnlyFillFreeSlots() = runTest(dispatcher) {
        photoStore.pickedPhoto = { photo("picked-${photoStore.pickedUris.size}.jpg") }
        val form = viewModel(draftHandle())
        runCurrent()
        assertEquals(1, form.state.value.photos.size)

        form.addPhotos(List(4) { mockk<Uri>() })
        advanceUntilIdle()

        assertEquals(2, photoStore.pickedUris.size)
        assertEquals(3, form.state.value.photos.size)
        assertFalse(form.state.value.hasPhotoSlots)
    }

    @Test fun pickedPhotosAreIgnoredWhileDraftIsLocked() = runTest(dispatcher) {
        val drafts = FakeDraftReports()
        drafts.save(input(listOf(photo("locked.jpg"))), DraftState.UNCONFIRMED)
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id")), drafts = drafts)
        advanceUntilIdle()
        assertFalse(form.state.value.editable)
        assertTrue("an unconfirmed draft can still be re-sent", form.state.value.canSubmit)

        form.addPhotos(listOf(mockk()))
        advanceUntilIdle()

        assertTrue(photoStore.pickedUris.isEmpty())
        assertEquals(1, form.state.value.photos.size)
    }

    @Test fun formIsLockedWhileSaving() = runTest(dispatcher) {
        val response = CompletableDeferred<com.company.fieldincidentreporter.model.Incident>()
        val repository = FakeIncidentRepository().apply { createResult = { response.await() } }
        val form = viewModel(draftHandle(), repository)
        runCurrent()
        assertTrue(form.state.value.editable)
        assertTrue(form.state.value.canSubmit)

        form.sendReport()
        runCurrent()
        assertFalse(form.state.value.editable)
        assertFalse(form.state.value.canSubmit)
        form.title("Changed while saving")
        assertEquals("Pipe", form.state.value.title)

        response.complete(testIncident())
        advanceUntilIdle()
    }

    @Test fun incidentLoadFailureShowsRetryAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeIncidentRepository().apply { getResult = { throw IOException("offline") } }
        val form = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "existing")), repository)
        advanceUntilIdle()
        assertTrue(form.state.value.loadError)
        assertNotNull(form.state.value.error)
        assertFalse(form.state.value.editable)
        assertFalse(form.state.value.canSubmit)

        repository.getResult = { testIncident(it) }
        form.retryLoad()
        advanceUntilIdle()
        assertFalse(form.state.value.loadError)
        assertNull(form.state.value.error)
        assertEquals("Pipe", form.state.value.title)
        assertTrue(form.state.value.editable)
    }

    @Test fun openedSavedDraftIsMarkedSavedBeforeItLoads() = runTest(dispatcher) {
        val form = viewModel(SavedStateHandle(mapOf(KEY_DRAFT_ID to "stable-request-id")))
        assertTrue(form.state.value.loading)
        assertTrue(form.state.value.hasSavedDraft)
        advanceUntilIdle()
    }

    @Test fun dataCallsRunOnIoDispatcher() = runTest(dispatcher) {
        val repository = FakeIncidentRepository()
        val drafts = FakeDraftReports()
        val form = viewModel(draftHandle(), repository, drafts)
        runCurrent()

        form.sendReport()
        advanceUntilIdle()
        val editing = viewModel(SavedStateHandle(mapOf(KEY_EDIT_ID to "existing")), repository, drafts)
        advanceUntilIdle()

        assertEquals("incident-1", form.state.value.savedId)
        assertEquals("Pipe", editing.state.value.title)
        assertEquals(setOf(io), repository.dispatchers)
        assertEquals(setOf(io), drafts.dispatchers)
        assertEquals(setOf(io), photoStore.dispatchers)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        repository: FakeIncidentRepository = FakeIncidentRepository(),
        drafts: FakeDraftReports = FakeDraftReports(),
    ) = IncidentFormViewModel(repository, photoStore, drafts, io, CoroutineScope(io), handle)

    private fun photo(name: String) = files.newFile(name).also { it.writeBytes(byteArrayOf(-1, -40, -1, -39)) }

    private fun input(photos: List<java.io.File>) = ReportInput(
        title = "Pipe", description = "Leaking", severity = Severity.HIGH,
        location = "Building A", newPhotos = photos, clientRequestId = "stable-request-id",
    )

    private fun draftHandle(editId: String = "", photoCount: Int = 1): SavedStateHandle {
        val photos = MutableList(photoCount) { SavedFormPhoto(path = photo("photo-${files.root.listFiles()?.size}.jpg").absolutePath) }
        if (editId.isNotBlank()) photos += SavedFormPhoto(id = "kept-photo", url = "/uploads/kept-photo.jpg")
        val form = SavedForm(
            title = "Pipe", description = "Leaking", severity = Severity.HIGH, location = "Building A",
            photos = photos, dirty = true, stored = false,
        )
        return SavedStateHandle(mapOf(
            KEY_EDIT_ID to editId, KEY_REQUEST_ID to "stable-request-id", KEY_FORM to form,
        ))
    }

    private fun savedForm(handle: SavedStateHandle) = requireNotNull(handle.get<SavedForm>(KEY_FORM))

    private fun httpFailure(code: Int) = HttpException(Response.error<Any>(code, "failure".toResponseBody()))
}
