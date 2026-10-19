package com.company.fieldincidentreporter.model

enum class DraftState { DRAFT, UNCONFIRMED }

data class DraftReport(
    val id: String,
    val input: ReportInput,
    val state: DraftState,
    val createdAt: Long,
)
