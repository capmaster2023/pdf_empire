package com.pdfpocket.lite.features.fillsign

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
import com.pdfpocket.lite.features.tools.ToolStatus
import com.pdfpocket.lite.pdf.FieldType
import com.pdfpocket.lite.pdf.FormFieldInfo
import com.pdfpocket.lite.pdf.FormToolbox
import com.pdfpocket.lite.pdf.SignaturePlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FillSignViewModel(private val container: AppContainer) : ViewModel() {

    /** Taille d'une page en points PDF. */
    data class PageSize(val width: Float, val height: Float)

    data class PlacedSignature(val placement: SignaturePlacement, val bitmap: Bitmap)

    sealed interface UiState {
        data object Empty : UiState
        data object Loading : UiState
        data class Ready(val name: String, val pageCount: Int) : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Empty)
    val state: StateFlow<UiState> = _state

    val fields = MutableStateFlow<List<FormFieldInfo>>(emptyList())
    val textValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val checkValues = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val choiceValues = MutableStateFlow<Map<String, String>>(emptyMap())

    val signatureBitmap = MutableStateFlow<Bitmap?>(null)
    val initialsBitmap = MutableStateFlow<Bitmap?>(null)
    val placed = MutableStateFlow<List<PlacedSignature>>(emptyList())

    /** null = navigation normale ; true/false = le prochain appui place le paraphe/la signature. */
    val placingInitials = MutableStateFlow<Boolean?>(null)

    val pageSizes = MutableStateFlow<List<PageSize>>(emptyList())
    val saveStatus = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    private var sourceFile: File? = null
    private var renderer: PdfRenderer? = null
    private val rendererMutex = Mutex()
    private var documentName: String = "document.pdf"

    private val pageCache = object : LruCache<Int, Bitmap>(40 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    fun open(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Loading
            saveStatus.value = ToolStatus.Idle
            placed.value = emptyList()
            try {
                closeRenderer()
                val document = container.documents.registerDocument(uri)
                documentName = document.name
                val file = FileUtils.copyToCache(container.app, uri, "fillsign")
                    ?: throw IOException("Copie impossible")
                sourceFile = file

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer

                val sizes = mutableListOf<PageSize>()
                for (index in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(index)
                    try {
                        sizes.add(PageSize(page.width.toFloat(), page.height.toFloat()))
                    } finally {
                        page.close()
                    }
                }
                pageSizes.value = sizes

                val detected = FormToolbox.loadFields(file)
                fields.value = detected
                textValues.value = detected
                    .filter { it.type == FieldType.TEXT }
                    .associate { it.name to it.initialValue }
                checkValues.value = detected
                    .filter { it.type == FieldType.CHECKBOX }
                    .associate { it.name to it.checked }
                choiceValues.value = detected
                    .filter { it.type == FieldType.CHOICE }
                    .associate { it.name to it.initialValue }

                container.documents.updatePageCount(uri.toString(), pdfRenderer.pageCount)
                _state.value = UiState.Ready(document.name, pdfRenderer.pageCount)
            } catch (_: Exception) {
                _state.value = UiState.Error
            }
        }
    }

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

    fun setTextValue(name: String, value: String) {
        textValues.value = textValues.value.toMutableMap().apply { put(name, value) }
    }

    fun toggleCheck(name: String) {
        val current = checkValues.value[name] ?: false
        checkValues.value = checkValues.value.toMutableMap().apply { put(name, !current) }
    }

    fun setChoice(name: String, value: String) {
        choiceValues.value = choiceValues.value.toMutableMap().apply { put(name, value) }
    }

    fun setSignature(bitmap: Bitmap, isInitials: Boolean) {
        if (isInitials) initialsBitmap.value = bitmap else signatureBitmap.value = bitmap
        placingInitials.value = isInitials
    }

    fun startPlacing(isInitials: Boolean) {
        placingInitials.value = isInitials
    }

    fun cancelPlacing() {
        placingInitials.value = null
    }

    /** Place la signature/paraphe courant à la position touchée (ratios 0..1, origine haut-gauche). */
    fun placeAt(pageIndex: Int, xRatio: Float, yTopRatio: Float) {
        val isInitials = placingInitials.value ?: return
        val bitmap = (if (isInitials) initialsBitmap.value else signatureBitmap.value) ?: return
        val widthRatio = if (isInitials) 0.15f else 0.35f
        val placement = SignaturePlacement(
            pageIndex = pageIndex,
            xRatio = (xRatio - widthRatio / 2f).coerceIn(0f, 1f - widthRatio),
            yTopRatio = yTopRatio.coerceIn(0f, 0.98f),
            widthRatio = widthRatio,
            isInitials = isInitials
        )
        placed.value = placed.value + PlacedSignature(placement, bitmap)
        placingInitials.value = null
    }

    fun resizeLast(widthRatio: Float) {
        val list = placed.value.toMutableList()
        if (list.isEmpty()) return
        val last = list.removeAt(list.size - 1)
        list.add(
            last.copy(
                placement = last.placement.copy(
                    widthRatio = widthRatio.coerceIn(0.08f, 0.9f)
                )
            )
        )
        placed.value = list
    }

    fun removeLastPlaced() {
        val list = placed.value.toMutableList()
        if (list.isNotEmpty()) {
            list.removeAt(list.size - 1)
            placed.value = list
        }
    }

    fun outputName(): String = "rempli_$documentName"

    fun save(output: Uri) {
        val file = sourceFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            saveStatus.value = ToolStatus.Working
            saveStatus.value = try {
                FormToolbox.applyAndSave(
                    context = container.app,
                    sourceFile = file,
                    textValues = textValues.value,
                    checkValues = checkValues.value,
                    choiceValues = choiceValues.value,
                    signatures = placed.value.map { it.placement to it.bitmap },
                    output = output
                )
                ToolStatus.Done(output, outputName())
            } catch (_: Exception) {
                ToolStatus.Error
            } catch (_: OutOfMemoryError) {
                ToolStatus.Error
            }
        }
    }

    fun shareResult() {
        val done = saveStatus.value as? ToolStatus.Done ?: return
        viewModelScope.launch(Dispatchers.IO) {
            FileUtils.shareDocument(container.app, done.output, done.outputName)
        }
    }

    private fun closeRenderer() {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        renderer = null
        pageCache.evictAll()
        sourceFile?.delete()
        sourceFile = null
    }

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
    }
}
