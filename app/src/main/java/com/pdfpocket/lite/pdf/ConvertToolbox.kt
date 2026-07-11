package com.pdfpocket.lite.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Conversions : PDF -> images (galerie), PDF -> texte (.txt),
 * extraction des images contenues dans le PDF (galerie).
 */
object ConvertToolbox {

    /**
     * Rend chaque page du PDF en PNG dans la galerie (album "PDF Empire").
     * Retourne le nombre d'images enregistrées.
     */
    @Throws(IOException::class)
    fun pdfToImages(context: Context, source: Uri, baseName: String): Int {
        val file = copyForRenderer(context, source)
        var saved = 0
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    val bitmap = try {
                        val targetWidth = 1600
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val rendered = Bitmap.createBitmap(
                            targetWidth, height, Bitmap.Config.ARGB_8888
                        )
                        rendered.eraseColor(Color.WHITE)
                        page.render(
                            rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        rendered
                    } finally {
                        page.close()
                    }
                    val name = "${baseName}_page_${index + 1}.png"
                    if (saveToGallery(context, bitmap, name)) saved++
                    bitmap.recycle()
                }
            }
        } finally {
            file.delete()
        }
        if (saved == 0) throw IOException("Aucune image enregistrée")
        return saved
    }

    /** Extrait tout le texte du PDF vers la destination (.txt). */
    @Throws(IOException::class)
    fun pdfToText(context: Context, source: Uri, output: Uri) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val text = stripper.getText(document)
                val outputStream = context.contentResolver.openOutputStream(output)
                    ?: throw IOException("Impossible d'ouvrir la destination")
                outputStream.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    /**
     * Extrait les images incrustées dans le PDF vers la galerie.
     * Retourne le nombre d'images enregistrées.
     */
    @Throws(IOException::class)
    fun extractImages(context: Context, source: Uri, baseName: String): Int {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        var saved = 0
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                for (pageIndex in 0 until document.numberOfPages) {
                    val resources = document.getPage(pageIndex).resources ?: continue
                    saved += extractFromResources(
                        context, resources, baseName, pageIndex, depth = 0
                    )
                }
            }
        }
        if (saved == 0) throw IOException("Aucune image trouvée dans ce PDF")
        return saved
    }

    private fun extractFromResources(
        context: Context,
        resources: PDResources,
        baseName: String,
        pageIndex: Int,
        depth: Int
    ): Int {
        if (depth > 2) return 0
        var saved = 0
        try {
            for (name in resources.xObjectNames) {
                when (val xObject = try {
                    resources.getXObject(name)
                } catch (_: Exception) {
                    null
                }) {
                    is PDImageXObject -> {
                        val bitmap = try {
                            xObject.image
                        } catch (_: Exception) {
                            null
                        } ?: continue
                        val fileName =
                            "${baseName}_p${pageIndex + 1}_${name.name}_${saved + 1}.png"
                        if (saveToGallery(context, bitmap, fileName)) saved++
                    }

                    is PDFormXObject -> {
                        val nested = try {
                            xObject.resources
                        } catch (_: Exception) {
                            null
                        }
                        if (nested != null) {
                            saved += extractFromResources(
                                context, nested, baseName, pageIndex, depth + 1
                            )
                        }
                    }

                    else -> Unit
                }
            }
        } catch (_: Exception) {
        }
        return saved
    }

    /** Enregistre un bitmap en PNG dans la galerie, album "PDF Empire". */
    private fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/PDF Empire"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                val out: OutputStream =
                    context.contentResolver.openOutputStream(uri) ?: return false
                out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } else {
                // API 26-28 : dossier images de l'application + indexation MediaScanner.
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "PDF Empire"
                ).apply { mkdirs() }
                val file = File(dir, displayName)
                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("image/png"), null
                )
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyForRenderer(context: Context, source: Uri): File {
        val dir = File(context.cacheDir, "convert").apply { mkdirs() }
        val file = File(dir, "src_${System.currentTimeMillis()}.pdf")
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            FileOutputStream(file).use { out -> stream.copyTo(out) }
        }
        return file
    }
}
