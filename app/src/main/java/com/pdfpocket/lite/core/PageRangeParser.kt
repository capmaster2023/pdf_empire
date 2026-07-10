package com.pdfpocket.lite.core

/**
 * Analyse une saisie de plage de pages du type :
 *   "1", "1-5", "1,3,7", "1-4,8-10"
 * Retourne la liste triée des pages (base 1) ou null si la saisie est invalide.
 */
object PageRangeParser {

    fun parse(input: String, pageCount: Int): List<Int>? {
        if (input.isBlank() || pageCount < 1) return null
        val pages = sortedSetOf<Int>()
        for (part in input.split(',')) {
            val token = part.trim()
            if (token.isEmpty()) return null
            if (token.contains('-')) {
                val bounds = token.split('-')
                if (bounds.size != 2) return null
                val start = bounds[0].trim().toIntOrNull() ?: return null
                val end = bounds[1].trim().toIntOrNull() ?: return null
                if (start < 1 || end > pageCount || start > end) return null
                for (p in start..end) pages.add(p)
            } else {
                val p = token.toIntOrNull() ?: return null
                if (p < 1 || p > pageCount) return null
                pages.add(p)
            }
        }
        return if (pages.isEmpty()) null else pages.toList()
    }
}
