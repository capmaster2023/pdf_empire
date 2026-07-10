package com.pdfpocket.lite.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.data.db.DocumentDao
import com.pdfpocket.lite.data.db.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao
) {

    val recents: Flow<List<DocumentEntity>> = dao.recents()
    val favorites: Flow<List<DocumentEntity>> = dao.favorites()
    val all: Flow<List<DocumentEntity>> = dao.all()

    /**
     * Enregistre (ou met à jour) un document dans l'historique.
     * Conserve les favoris et la dernière page si le document existe déjà.
     */
    suspend fun registerDocument(uri: Uri): DocumentEntity {
        persistReadPermission(uri)
        val (name, size) = FileUtils.queryMeta(context, uri)
        val existing = dao.byUri(uri.toString())
        val entity = DocumentEntity(
            uri = uri.toString(),
            name = name,
            sizeBytes = if (size > 0) size else existing?.sizeBytes ?: 0L,
            pageCount = existing?.pageCount ?: 0,
            lastPage = existing?.lastPage ?: 0,
            lastOpenedAt = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun updateLastPage(uri: String, page: Int) = dao.updateLastPage(uri, page)

    suspend fun updatePageCount(uri: String, count: Int) = dao.updatePageCount(uri, count)

    suspend fun setFavorite(uri: String, favorite: Boolean) = dao.setFavorite(uri, favorite)

    /** Retire le document de l'historique. Le fichier réel n'est jamais supprimé. */
    suspend fun removeFromHistory(uri: String) = dao.delete(uri)

    suspend fun clearHistory() = dao.clear()

    private fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Certains URI (ex. partage direct) ne sont pas persistables : non bloquant.
        }
    }
}
