package com.pdfpocket.lite.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.IOException

/**
 * Gestion des pages : le nouvel agencement est décrit par une liste de PageSpec,
 * puis le document final est reconstruit page par page (réordonner, tourner,
 * supprimer, dupliquer, insérer une page blanche ou les pages d'un autre PDF).
 */
object PageToolbox {

    /** Clé de document réservée aux pages blanches insérées. */
    const val BLANK_DOC_KEY = -1

    /**
     * Une page du document final :
     * [docKey] identifie le PDF source (BLANK_DOC_KEY = page blanche),
     * [pageIndex] la page dans ce PDF (base 0),
     * [addedRotation] la rotation ajoutée par l'utilisateur (multiple de 90).
     */
    data class PageSpec(
        val docKey: Int,
        val pageIndex: Int,
        val addedRotation: Int
    )

    /**
     * Reconstruit un PDF selon [specs]. [sources] mappe chaque docKey vers son URI.
     * La suppression = ne pas inclure la page ; la duplication = l'inclure deux fois.
     */
    @Throws(IOException::class)
    fun rebuild(
        context: Context,
        sources: Map<Int, Uri>,
        specs: List<PageSpec>,
        output: Uri
    ) {
        if (specs.isEmpty()) throw IOException("Aucune page à enregistrer")
        val documents = mutableMapOf<Int, PDDocument>()
        try {
            for ((key, uri) in sources) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Impossible de lire un des documents")
                documents[key] = PDDocument.load(
                    input, MemoryUsageSetting.setupTempFileOnly()
                )
            }
            PDDocument().use { result ->
                for (spec in specs) {
                    if (spec.docKey == BLANK_DOC_KEY) {
                        result.addPage(PDPage(PDRectangle.A4))
                        continue
                    }
                    val source = documents[spec.docKey] ?: continue
                    if (spec.pageIndex < 0 || spec.pageIndex >= source.numberOfPages) continue
                    val imported = result.importPage(source.getPage(spec.pageIndex))
                    if (spec.addedRotation % 360 != 0) {
                        imported.rotation =
                            ((imported.rotation + spec.addedRotation) % 360 + 360) % 360
                    }
                }
                if (result.numberOfPages == 0) throw IOException("Document final vide")
                val outputStream = context.contentResolver.openOutputStream(output)
                    ?: throw IOException("Impossible d'ouvrir la destination")
                // Enregistrer AVANT de fermer les sources : importPage garde des
                // références vers les documents d'origine.
                outputStream.use { out -> result.save(out) }
            }
        } finally {
            for (document in documents.values) {
                try {
                    document.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
