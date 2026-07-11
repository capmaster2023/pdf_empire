package com.pdfpocket.lite.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import java.io.File

/**
 * Extraction des annotations de lien d'un PDF pour les rendre cliquables
 * dans la visionneuse (le rendu bitmap de PdfRenderer ne les conserve pas).
 */
object LinkToolbox {

    /**
     * Zone cliquable en ratios de page (origine haut-gauche).
     * [url] pour un lien externe, [targetPage] (base 0) pour un lien interne.
     */
    data class LinkArea(
        val x: Float,
        val yTop: Float,
        val width: Float,
        val height: Float,
        val url: String?,
        val targetPage: Int?
    )

    /** Liens de chaque page : index de page (base 0) -> zones cliquables. */
    fun extractLinks(file: File): Map<Int, List<LinkArea>> {
        val result = HashMap<Int, MutableList<LinkArea>>()
        try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                for (pageIndex in 0 until document.numberOfPages) {
                    val page = document.getPage(pageIndex)
                    val mediaBox = page.mediaBox ?: continue
                    val annotations = try {
                        page.annotations
                    } catch (_: Exception) {
                        continue
                    }
                    for (annotation in annotations) {
                        if (annotation !is PDAnnotationLink) continue
                        val rect = annotation.rectangle ?: continue
                        var url: String? = null
                        var targetPage: Int? = null
                        try {
                            when (val action = annotation.action) {
                                is PDActionURI -> url = action.uri
                                is PDActionGoTo -> {
                                    targetPage = resolveDestination(
                                        document, action.destination
                                    )
                                }

                                else -> {
                                    // Lien sans action : destination directe possible.
                                    targetPage = resolveDestination(
                                        document, annotation.destination
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            continue
                        }
                        if (url == null && targetPage == null) continue
                        val pageWidth = mediaBox.width.coerceAtLeast(1f)
                        val pageHeight = mediaBox.height.coerceAtLeast(1f)
                        result.getOrPut(pageIndex) { ArrayList() }.add(
                            LinkArea(
                                x = ((rect.lowerLeftX - mediaBox.lowerLeftX) / pageWidth)
                                    .coerceIn(0f, 1f),
                                yTop = ((mediaBox.upperRightY - rect.upperRightY) / pageHeight)
                                    .coerceIn(0f, 1f),
                                width = (rect.width / pageWidth).coerceIn(0.005f, 1f),
                                height = (rect.height / pageHeight).coerceIn(0.005f, 1f),
                                url = url,
                                targetPage = targetPage
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Document illisible : aucun lien.
        }
        return result
    }

    private fun resolveDestination(document: PDDocument, destination: Any?): Int? {
        return try {
            when (destination) {
                is PDPageDestination -> {
                    val number = destination.retrievePageNumber()
                    if (number >= 0) number else null
                }

                is PDNamedDestination -> {
                    val resolved =
                        document.documentCatalog.findNamedDestinationPage(destination)
                    val number = resolved?.retrievePageNumber() ?: -1
                    if (number >= 0) number else null
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
