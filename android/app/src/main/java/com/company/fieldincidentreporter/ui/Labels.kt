package com.company.fieldincidentreporter.ui

import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.model.Status

val Severity.label: String get() = name.toLabel()

val Status.label: String get() = name.toLabel()

/** IN_PROGRESS -> "In progress". */
private fun String.toLabel() = replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
