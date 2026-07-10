package com.pdfpocket.lite.core

object FileNameValidator {

    private val illegalChars = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")

    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && !illegalChars.containsMatchIn(trimmed)
    }

    /** Nettoie un nom de fichier et garantit l'extension .pdf. */
    fun sanitize(name: String): String {
        var clean = name.trim().replace(illegalChars, "_")
        if (clean.isEmpty() || clean == ".pdf") clean = "document.pdf"
        if (!clean.lowercase().endsWith(".pdf")) clean += ".pdf"
        return clean
    }
}
