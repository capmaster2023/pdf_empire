package com.pdfpocket.lite.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object FileUtils {

    /** Nom affichable + taille (octets) d'un URI, avec valeurs de repli sûres. */
    fun queryMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
            // Métadonnées indisponibles : on garde les valeurs de repli.
        }
        return name to size
    }

    /** Copie le contenu d'un URI dans le cache et retourne le fichier, ou null en cas d'échec. */
    fun copyToCache(context: Context, uri: Uri, dirName: String = "docs"): File? {
        return try {
            val dir = File(context.cacheDir, dirName).apply { mkdirs() }
            val file = File(dir, "doc_" + uri.toString().hashCode().toString().replace('-', 'n') + ".pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
            file
        } catch (_: Exception) {
            null
        }
    }

    /** Partage un PDF via une copie exposée par FileProvider. */
    fun shareDocument(context: Context, uri: Uri, displayName: String) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, FileNameValidator.sanitize(displayName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return
            val shareUri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Le partage a échoué silencieusement : rien de destructif.
        }
    }

    fun clearAppCache(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes o"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f Ko", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f Mo", mb)
        return String.format(Locale.ROOT, "%.2f Go", mb / 1024.0)
    }
}
