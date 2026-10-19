package com.company.fieldincidentreporter

import com.company.fieldincidentreporter.data.IncidentApi
import com.company.fieldincidentreporter.data.NetworkIncidentRepository
import com.company.fieldincidentreporter.model.ReportInput
import com.company.fieldincidentreporter.model.Result
import com.company.fieldincidentreporter.model.Severity
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class IncidentRepositoryTest {
    @Test fun createSendsStableIdAndPhotoMultipart() = runBlocking {
        val server = MockWebServer()
        server.start()
        val photo = File.createTempFile("incident-test", ".jpg")
        try {
            photo.writeBytes(byteArrayOf(-1, -40, -1, -39))
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"id":"abc","title":"Pipe","description":"Leaking","severity":"HIGH","location":"Building A","status":"OPEN","photos":[{"id":"p1","url":"/uploads/p1.jpg"}],"createdAt":"2026-10-18T00:00:00Z","updatedAt":"2026-10-18T00:00:00Z"}"""
            ))
            val json = Json { ignoreUnknownKeys = true }
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(IncidentApi::class.java)
            val saved = NetworkIncidentRepository(api, json).create(ReportInput(
                title = "Pipe", description = "Leaking", severity = Severity.HIGH,
                location = "Building A", newPhotos = listOf(photo), clientRequestId = "same-id",
            ))
            assertEquals("abc", (saved as Result.Success).data.id)
            assertEquals(BuildConfig.API_BASE_URL.trimEnd('/') + "/uploads/p1.jpg", saved.data.photos.single().url)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"clientRequestId\""))
            assertTrue(body.contains("same-id"))
            assertTrue(body.contains("name=\"photos\""))
        } finally {
            photo.delete()
            server.shutdown()
        }
    }
}
