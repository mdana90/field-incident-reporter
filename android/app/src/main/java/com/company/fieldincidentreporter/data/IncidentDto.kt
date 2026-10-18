package com.company.fieldincidentreporter.data

import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.model.Photo
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status
import kotlinx.serialization.Serializable

@Serializable
data class PhotoDto(val id: String, val url: String)

@Serializable
data class IncidentDto(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val location: String,
    val status: String,
    val photos: List<PhotoDto>,
    val createdAt: String,
    val updatedAt: String,
)

fun IncidentDto.toModel() = Incident(
    id = id,
    title = title,
    description = description,
    severity = Severity.valueOf(severity),
    location = location,
    status = Status.valueOf(status),
    photos = photos.map { Photo(it.id, it.url) },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
