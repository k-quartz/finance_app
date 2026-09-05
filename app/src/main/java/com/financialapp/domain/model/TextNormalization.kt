package com.financialapp.domain.model

fun String.toTitleCase(): String = trim().split(Regex("\\s+")).joinToString(" ") { word ->
    word.lowercase().replaceFirstChar { it.uppercase() }
}
