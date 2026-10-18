package com.company.fieldincidentreporter.model

data class Incident(
    val id: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val location: String,
    val status: Status,
    val photos: List<Photo>,
    val createdAt: String,
    val updatedAt: String,
)

data class Photo(val id: String, val url: String)

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

enum class Status { OPEN, IN_PROGRESS, RESOLVED }
