package com.company.fieldincidentreporter.data

import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.ReportInput
import com.company.fieldincidentreporter.model.Result
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

interface IncidentRepository {
    suspend fun list(): Result<List<Incident>>
    suspend fun get(id: String): Result<Incident>
    suspend fun create(input: ReportInput): Result<Incident>
    suspend fun update(id: String, input: ReportInput): Result<Incident>
    suspend fun delete(id: String): Result<Unit>
}

class NetworkIncidentRepository @Inject constructor(
    private val api: IncidentApi,
    private val json: Json,
) : IncidentRepository {
    override suspend fun list() = resultOf { api.list().map(IncidentDto::toModel) }

    override suspend fun get(id: String) = resultOf { api.get(id).toModel() }

    override suspend fun create(input: ReportInput) = resultOf {
        require(!input.clientRequestId.isNullOrBlank())
        api.create(fields(input) + ("clientRequestId" to input.clientRequestId.toPart()), photos(input)).toModel()
    }

    override suspend fun update(id: String, input: ReportInput) = resultOf {
        val keep = json.encodeToString(input.keepPhotoIds)
        api.update(id, fields(input) + ("keepPhotoIds" to keep.toPart()), photos(input)).toModel()
    }

    override suspend fun delete(id: String): Result<Unit> = resultOf { api.delete(id) }

    private fun fields(input: ReportInput): Map<String, RequestBody> = mapOf(
        "title" to input.title.toPart(),
        "description" to input.description.toPart(),
        "severity" to input.severity.name.toPart(),
        "location" to input.location.toPart(),
    )

    private fun photos(input: ReportInput): List<MultipartBody.Part> = input.newPhotos.map { file ->
        MultipartBody.Part.createFormData("photos", file.name, file.asRequestBody("image/jpeg".toMediaType()))
    }

    private fun String.toPart() = toRequestBody("text/plain".toMediaType())
}
