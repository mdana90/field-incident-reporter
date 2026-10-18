package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.resultOf
import com.company.fieldincidentreporter.model.Result
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

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

    @Test fun cancellationIsRethrownNotTurnedIntoError() {
        assertThrows(CancellationException::class.java) {
            runBlocking { resultOf { throw CancellationException("cancelled") } }
        }
    }
}
