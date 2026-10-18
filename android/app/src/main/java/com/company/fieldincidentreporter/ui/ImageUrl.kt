package com.company.fieldincidentreporter.ui

import com.company.fieldincidentreporter.BuildConfig

fun imageUrl(path: String): String = BuildConfig.API_BASE_URL.trimEnd('/') +
    (if (path.startsWith('/')) path else "/$path")
