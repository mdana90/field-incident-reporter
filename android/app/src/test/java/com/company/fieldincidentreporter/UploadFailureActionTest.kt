package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.offline.UploadFailureAction
import com.company.fieldincidentreporter.data.offline.uploadFailureAction
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class UploadFailureActionTest {
    @Test fun connectionFailureRetries() {
        assertEquals(UploadFailureAction.RETRY, uploadFailureAction(IOException("Server stopped")))
    }

    @Test fun serverFailureRetries() {
        assertEquals(UploadFailureAction.RETRY, uploadFailureAction(httpFailure(503)))
    }

    @Test fun validationFailureNeedsAttention() {
        assertEquals(UploadFailureAction.NEEDS_ATTENTION, uploadFailureAction(httpFailure(400)))
    }

    private fun httpFailure(code: Int) = HttpException(Response.error<Any>(code, "failure".toResponseBody()))
}
