package com.pdfpocket.lite.features.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.core.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ViewerViewModel(
    private val container: AppContainer,
    val uriString: String
) : ViewModel() {

    enum class ErrorType { PROTECTED, UNREADABLE }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(
            val pageCount: Int,
            val name: String,
            val lastPage: Int
        ) : UiState

        data class Error(val type: ErrorType) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private var renderer: PdfRenderer? = null
    private var cachedFile: File? = null
    private val rendererMutex = Mutex()

    // Cache limité (~48 Mo) avec recyclage automatique par éviction.
    private val pageCache = object : LruCache<Int, Bitmap>(48 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    init {
        openDocument()
    }

    private fun openDocument() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val document = container.documents.registerDocument(uri)
                _isFavorite.value = document.isFavorite

                // Copie locale : garantit un descripteur seekable pour PdfRenderer.
                val file = FileUtils.copyToCache(container.app, uri)
                    ?: throw IOException("Copie impossible")
                cachedFile = file
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer

                container.documents.updatePageCount(uriString, pdfRenderer.pageCount)
                _state.value = UiState.Ready(
                    pageCount = pdfRenderer.pageCount,
                    name = document.name,
                    lastPage = document.lastPage.coerceIn(0, pdfRenderer.pageCount - 1)
                )
            } catch (security: SecurityException) {
                _state.value = UiState.Error(ErrorType.PROTECTED)
            } catch (error: Exception) {
                _state.value = UiState.Error(ErrorType.UNREADABLE)
            }
        }
    }

    /** Rend une page à la largeur demandée. Rendu séquentiel (PdfRenderer non thread-safe). */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            pageCache.get(index)?.let { return@withContext it }
            rendererMutex.withLock {
                pageCache.get(index)?.let { return@withLock it }
                val pdfRenderer = renderer ?: return@withLock null
                if (index < 0 || index >= pdfRenderer.pageCount) return@withLock null
                try {
                    val page = pdfRenderer.openPage(index)
                    try {
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(
                            targetWidth.coerceAtLeast(1), height, Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageCache.put(index, bitmap)
                        bitmap
                    } finally {
                        page.close()
                    }
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    pageCache.evictAll()
                    null
                }
            }
        }

    fun onPageVisible(page: Int) {
        viewModelScope.launch {
            container.documents.updateLastPage(uriString, page)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val next = !_isFavorite.value
            _isFavorite.value = next
            container.documents.setFavorite(uriString, next)
        }
    }

    fun share(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FileUtils.shareDocument(container.app, Uri.parse(uriString), name)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        pageCache.evictAll()
        cachedFile?.delete()
    }
}
