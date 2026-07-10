package com.pdfpocket.lite.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException

/**
 * Opérations PDF réelles (aucun bouton factice).
 * Fusion et extraction : Apache PDFBox (port Android, licence Apache 2.0).
 * Images -> PDF : android.graphics.pdf.PdfDocument (API système).
 */
object PdfToolbox {

    /** Nombre de pages d'un PDF, ou lève une exception si illisible/protégé. */
    @Throws(IOException::class, SecurityException::class)
    fun pageCount(context: Context, uri: Uri): Int {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("URI illisible")
        // PdfRenderer prend possession du descripteur et le ferme lui-même.
        PdfRenderer(pfd).use { renderer ->
            return renderer.pageCount
        }
    }

    /** Fusionne plusieurs PDF (dans l'ordre fourni) vers un URI de sortie SAF. */
    @Throws(IOException::class)
    fun merge(context: Context, sources: List<Uri>, output: Uri) {
        if (sources.size < 2) throw IOException("Au moins deux documents sont requis")
        val merger = PDFMergerUtility()
        val outputStream = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Impossible d'ouvrir la destination")
        outputStream.use { out ->
            merger.destinationStream = out
            for (source in sources) {
                val input = context.contentResolver.openInputStream(source)
                    ?: throw IOException("Impossible de lire un des documents")
                merger.addSource(input)
            }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        }
    }

    /** Extrait les pages demandées (base 1) vers un nouveau PDF. Fichier original conservé. */
    @Throws(IOException::class)
    fun extractPages(context: Context, source: Uri, pages: List<Int>, output: Uri) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                PDDocument().use { result ->
                    for (page in pages) {
                        if (page < 1 || page > document.numberOfPages) {
                            throw IOException("Page hors limites : $page")
                        }
                        result.importPage(document.getPage(page - 1))
                    }
                    val outputStream = context.contentResolver.openOutputStream(output)
                        ?: throw IOException("Impossible d'ouvrir la destination")
                    outputStream.use { out -> result.save(out) }
                }
            }
        }
    }

    /** Convertit une liste d'images (JPEG/PNG/WebP) en PDF multipage. */
    @Throws(IOException::class)
    fun imagesToPdf(context: Context, images: List<Uri>, output: Uri) {
        if (images.isEmpty()) throw IOException("Aucune image sélectionnée")
        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            for (imageUri in images) {
                val bitmap = decodeScaledBitmap(context, imageUri, maxDimension = 2048)
                    ?: throw IOException("Image illisible")
                val pageInfo = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, pageNumber)
                    .create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                bitmap.recycle()
                pageNumber++
            }
            val outputStream = context.contentResolver.openOutputStream(output)
                ?: throw IOException("Impossible d'ouvrir la destination")
            outputStream.use { out -> pdf.writeTo(out) }
        } finally {
            pdf.close()
        }
    }

    /** Décode une image avec sous-échantillonnage pour maîtriser la mémoire. */
    fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxDimension / 2 ||
                bounds.outHeight / (sampleSize * 2) >= maxDimension / 2
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
