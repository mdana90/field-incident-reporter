package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DeliveryState
import com.company.fieldincidentreporter.data.offline.PendingUploadResult
import com.company.fieldincidentreporter.data.offline.uploadPendingReport
import com.company.fieldincidentreporter.model.Severity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response

class UploadPendingReportTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun successfulUploadCompletesThePendingReport() = runTest {
        val input = reportInput()
        val store = FakePendingReports(input)
        val repository = FakeIncidentRepository()

        assertEquals(PendingUploadResult.SUCCESS, uploadPendingReport("request-id", store, repository))
        assertTrue(store.sending)
        assertTrue(store.completed)
        assertEquals(input, repository.creates.single())
    }

    @Test fun connectionFailureWaitsForRetry() = runTest {
        val store = FakePendingReports(reportInput())
        val repository = FakeIncidentRepository().apply { createResult = { throw IOException("offline") } }

        assertEquals(PendingUploadResult.RETRY, uploadPendingReport("request-id", store, repository))
        assertEquals(listOf(DeliveryState.WAITING), store.states)
        assertFalse(store.completed)
    }

    @Test fun serverValidationFailureNeedsAttention() = runTest {
        val store = FakePendingReports(reportInput())
        val repository = FakeIncidentRepository().apply {
            createResult = { throw HttpException(Response.error<Any>(400, "bad".toResponseBody())) }
        }

        assertEquals(PendingUploadResult.SUCCESS, uploadPendingReport("request-id", store, repository))
        assertEquals(listOf(DeliveryState.NEEDS_ATTENTION), store.states)
        assertFalse(store.completed)
    }

    @Test fun missingPhotoNeedsAttentionWithoutAnUpload() = runTest {
        val input = reportInput().copy(newPhotos = listOf(files.root.resolve("missing.jpg")))
        val store = FakePendingReports(input)
        val repository = FakeIncidentRepository()

        assertEquals(PendingUploadResult.SUCCESS, uploadPendingReport("request-id", store, repository))
        assertEquals(listOf(DeliveryState.NEEDS_ATTENTION), store.states)
        assertTrue(repository.creates.isEmpty())
    }

    @Test fun cancellationDoesNotMarkTheReportAsRejected() = runTest {
        val store = FakePendingReports(reportInput())
        val repository = FakeIncidentRepository().apply { createResult = { throw CancellationException("stopped") } }

        try {
            uploadPendingReport("request-id", store, repository)
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(store.sending)
            assertTrue(store.states.isEmpty())
            assertFalse(store.completed)
        }
    }

    private fun reportInput(): ReportInput {
        val photo = files.newFile("photo.jpg")
        photo.writeBytes(byteArrayOf(-1, -40, -1, -39))
        return ReportInput(
            title = "Pipe",
            description = "Leaking",
            severity = Severity.HIGH,
            location = "Building A",
            newPhotos = listOf(photo),
            clientRequestId = "request-id",
        )
    }
}
