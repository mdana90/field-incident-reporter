package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.PhotoException
import com.company.fieldincidentreporter.data.isRejection
import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.data.userMessage
import com.company.fieldincidentreporter.model.Result
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ResultOfTest {
    @Test fun successWrapsValue() = runTest {
        assertEquals("ok", (resultOf { "ok" } as Result.Success).data)
    }

    @Test fun failureBecomesErrorWithUserMessage() = runTest {
        val failure = IOException("offline")
        val result = resultOf { throw failure } as Result.Error
        assertSame(failure, result.exception)
        assertEquals("Could not reach the server. Check the connection and try again.", result.message)
    }

    @Test fun photoFailureShowsItsOwnMessageWhateverTheWording() {
        assertEquals("Image must be a JPEG", PhotoException("Image must be a JPEG").userMessage())
    }

    @Test fun networkFailureShowsConnectionMessageEvenIfWordedLikePhotoError() {
        assertEquals(
            "Could not reach the server. Check the connection and try again.",
            IOException("Photo upload stream closed").userMessage(),
        )
    }

    @Test fun onlyRequestRefusalsCountAsRejection() {
        listOf(400, 413, 422).forEach { assertTrue("$it", httpFailure(it).isRejection()) }
        listOf(404, 500, 503).forEach { assertFalse("$it", httpFailure(it).isRejection()) }
        assertFalse(IOException("offline").isRejection())
    }

    @Test fun cancellationIsRethrownNotTurnedIntoError() {
        assertThrows(CancellationException::class.java) {
            runBlocking { resultOf { throw CancellationException("cancelled") } }
        }
    }

    private fun httpFailure(code: Int) = HttpException(Response.error<Any>(code, "failure".toResponseBody()))
}
