package com.pdfpocket.lite.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * Recherche de texte dans un PDF avec positions des occurrences,
 * exprimées en ratios de page (0..1, origine haut-gauche) pour un
 * surlignage indépendant de la résolution de rendu.
 */
object SearchToolbox {

    /** Rectangle en ratios de page (origine haut-gauche). */
    data class RectRatio(
        val x: Float,
        val yTop: Float,
        val width: Float,
        val height: Float
    )

    /** Une occurrence : page (base 0), rectangles à surligner, extrait de contexte. */
    data class Match(
        val pageIndex: Int,
        val rects: List<RectRatio>,
        val snippet: String
    )

    /** Stripper qui collecte chaque caractère avec sa position. */
    private class PositionCollector : PDFTextStripper() {
        val text = StringBuilder()
        val positions = ArrayList<TextPosition>()

        init {
            sortByPosition = true
        }

        override fun writeString(string: String, textPositions: List<TextPosition>) {
            text.append(string)
            positions.addAll(textPositions)
            // Séparateur pour éviter de coller deux fragments distincts,
            // sans position associée (ignoré au mappage des rectangles).
            text.append(' ')
            super.writeString(string, textPositions)
        }
    }

    /**
     * Cherche [query] (insensible à la casse) dans [file].
     * Limité à [maxMatches] occurrences pour rester réactif.
     */
    fun search(file: File, query: String, maxMatches: Int = 300): List<Match> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val matches = ArrayList<Match>()
        try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                for (pageIndex in 0 until document.numberOfPages) {
                    if (matches.size >= maxMatches) break
                    val collector = PositionCollector()
                    collector.startPage = pageIndex + 1
                    collector.endPage = pageIndex + 1
                    try {
                        collector.getText(document)
                    } catch (_: Exception) {
                        continue
                    }
                    findInPage(collector, needle, pageIndex, matches, maxMatches)
                }
            }
        } catch (_: Exception) {
            // Document illisible : aucune occurrence.
        } catch (_: OutOfMemoryError) {
        }
        return matches
    }

    private fun findInPage(
        collector: PositionCollector,
        needle: String,
        pageIndex: Int,
        matches: MutableList<Match>,
        maxMatches: Int
    ) {
        // Reconstruit la chaîne caractère par caractère depuis les TextPositions,
        // pour que chaque index de la chaîne corresponde à une position connue.
        val builder = StringBuilder()
        val charToPosition = ArrayList<TextPosition>()
        for (position in collector.positions) {
            val unicode = position.unicode ?: continue
            for (character in unicode) {
                builder.append(character)
                charToPosition.add(position)
            }
        }
        val haystack = builder.toString().lowercase()
        var start = 0
        while (matches.size < maxMatches) {
            val found = haystack.indexOf(needle, start)
            if (found < 0) break
            val end = found + needle.length
            val rects = buildRects(charToPosition.subList(found, end))
            if (rects.isNotEmpty()) {
                val snippetStart = (found - 40).coerceAtLeast(0)
                val snippetEnd = (end + 40).coerceAtMost(builder.length)
                val snippet = builder.substring(snippetStart, snippetEnd)
                    .replace('\n', ' ')
                    .trim()
                matches.add(Match(pageIndex, rects, snippet))
            }
            start = end
        }
    }

    /** Regroupe les caractères par ligne et fusionne en rectangles. */
    private fun buildRects(positions: List<TextPosition>): List<RectRatio> {
        if (positions.isEmpty()) return emptyList()
        val rects = ArrayList<RectRatio>()
        var lineStart: TextPosition? = null
        var lineEnd: TextPosition? = null
        fun flush() {
            val first = lineStart ?: return
            val last = lineEnd ?: return
            val pageWidth = first.pageWidth.coerceAtLeast(1f)
            val pageHeight = first.pageHeight.coerceAtLeast(1f)
            val height = maxOf(first.heightDir, last.heightDir, 4f)
            val left = first.xDirAdj
            val right = last.xDirAdj + last.widthDirAdj
            // yDirAdj = ligne de base depuis le haut de la page.
            val top = minOf(first.yDirAdj, last.yDirAdj) - height
            rects.add(
                RectRatio(
                    x = (left / pageWidth).coerceIn(0f, 1f),
                    yTop = (top / pageHeight).coerceIn(0f, 1f),
                    width = ((right - left) / pageWidth).coerceIn(0.002f, 1f),
                    height = ((height * 1.25f) / pageHeight).coerceIn(0.004f, 1f)
                )
            )
        }
        for (position in positions) {
            val currentStart = lineStart
            if (currentStart == null) {
                lineStart = position
                lineEnd = position
                continue
            }
            val sameLine =
                kotlin.math.abs(position.yDirAdj - currentStart.yDirAdj) <
                    maxOf(currentStart.heightDir, 4f)
            if (sameLine) {
                lineEnd = position
            } else {
                flush()
                lineStart = position
                lineEnd = position
            }
        }
        flush()
        return rects
    }
}
