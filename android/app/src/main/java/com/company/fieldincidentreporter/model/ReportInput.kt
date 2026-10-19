package com.company.fieldincidentreporter.model

import java.io.File

data class ReportInput(
    val title: String,
    val description: String,
    val severity: Severity,
    val location: String,
    val newPhotos: List<File>,
    val keepPhotoIds: List<String> = emptyList(),
    val clientRequestId: String? = null,
)
