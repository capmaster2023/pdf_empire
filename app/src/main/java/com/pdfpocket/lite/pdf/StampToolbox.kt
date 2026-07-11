package com.pdfpocket.lite.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.IOException

/**
 * Filigranes et numérotation : texte en diagonale semi-transparent,
 * numéros de page, en-tête et pied de page.
 */
object StampToolbox {

    data class Options(
        val watermarkText: String = "",
        val watermarkOpacity: Float = 0.15f,
        val pageNumbers: Boolean = false,
        val headerText: String = "",
        val footerText: String = ""
    )

    @Throws(IOException::class)
    fun apply(context: Context, source: Uri, options: Options, output: Uri) {
        val hasWork = options.watermarkText.isNotBlank() ||
            options.pageNumbers ||
            options.headerText.isNotBlank() ||
            options.footerText.isNotBlank()
        if (!hasWork) throw IOException("Aucune option sélectionnée")

        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val font = PDType1Font.HELVETICA
                val boldFont = PDType1Font.HELVETICA_BOLD
                val total = document.numberOfPages
                for (index in 0 until total) {
                    val page = document.getPage(index)
                    val box = page.mediaBox ?: continue
                    try {
                        PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { content ->
                            if (options.watermarkText.isNotBlank()) {
                                drawWatermark(
                                    content, boldFont,
                                    sanitize(options.watermarkText),
                                    box.width, box.height,
                                    options.watermarkOpacity
                                )
                            }
                            if (options.headerText.isNotBlank()) {
                                drawCentered(
                                    content, font, sanitize(options.headerText),
                                    box.width, y = box.height - 24f, fontSize = 10f
                                )
                            }
                            if (options.footerText.isNotBlank()) {
                                drawCentered(
                                    content, font, sanitize(options.footerText),
                                    box.width, y = 18f, fontSize = 10f
                                )
                            }
                            if (options.pageNumbers) {
                                val label = "${index + 1} / $total"
                                val width = font.getStringWidth(label) / 1000f * 10f
                                content.beginText()
                                content.setFont(font, 10f)
                                content.newLineAtOffset(box.width - width - 24f, 18f)
                                content.showText(label)
                                content.endText()
                            }
                        }
                    } catch (_: Exception) {
                        // Une page récalcitrante ne bloque pas les autres.
                    }
                }
                val outputStream = context.contentResolver.openOutputStream(output)
                    ?: throw IOException("Impossible d'ouvrir la destination")
                outputStream.use { out -> document.save(out) }
            }
        }
    }

    private fun drawWatermark(
        content: PDPageContentStream,
        font: PDFont,
        text: String,
        pageWidth: Float,
        pageHeight: Float,
        opacity: Float
    ) {
        // Taille pour que le texte tienne sur la diagonale.
        val diagonal = kotlin.math.sqrt(
            (pageWidth * pageWidth + pageHeight * pageHeight).toDouble()
        ).toFloat()
        var fontSize = 60f
        val textWidth = font.getStringWidth(text) / 1000f
        if (textWidth * fontSize > diagonal * 0.7f) {
            fontSize = (diagonal * 0.7f / textWidth).coerceIn(14f, 60f)
        }
        val graphicsState = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = opacity.coerceIn(0.03f, 0.6f)
        }
        content.saveGraphicsState()
        content.setGraphicsStateParameters(graphicsState)
        content.setNonStrokingColor(120, 120, 120)
        val angle = Math.toRadians(45.0)
        content.beginText()
        content.setFont(font, fontSize)
        // Rotation autour du centre de la page, texte centré sur ce point.
        val half = textWidth * fontSize / 2f
        content.setTextMatrix(
            Matrix.getRotateInstance(
                angle,
                pageWidth / 2f - (half * kotlin.math.cos(angle)).toFloat(),
                pageHeight / 2f - (half * kotlin.math.sin(angle)).toFloat()
            )
        )
        try {
            content.showText(text)
        } catch (_: Exception) {
        }
        content.endText()
        content.restoreGraphicsState()
    }

    private fun drawCentered(
        content: PDPageContentStream,
        font: PDFont,
        text: String,
        pageWidth: Float,
        y: Float,
        fontSize: Float
    ) {
        val width = font.getStringWidth(text) / 1000f * fontSize
        content.beginText()
        content.setFont(font, fontSize)
        content.newLineAtOffset(((pageWidth - width) / 2f).coerceAtLeast(12f), y)
        try {
            content.showText(text)
        } catch (_: Exception) {
        }
        content.endText()
    }

    /** Remplace les caractères non encodables en WinAnsi par '?'. */
    private fun sanitize(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            builder.append(if (character.code in 32..255) character else '?')
        }
        return builder.toString()
    }
}
