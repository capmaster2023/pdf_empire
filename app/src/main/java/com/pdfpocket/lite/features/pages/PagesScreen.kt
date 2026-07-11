package com.pdfpocket.lite.features.pages

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.FileNameValidator
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.features.tools.ToolStatus
import com.pdfpocket.lite.pdf.PageToolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PagesViewModel(private val container: AppContainer) : ViewModel() {

    data class PageItem(
        val id: Long,
        val docKey: Int,
        val pageIndex: Int,
        val rotation: Int
    )

    data class Source(val name: String)

    val source = MutableStateFlow<Source?>(null)
    val items = MutableStateFlow<List<PageItem>>(emptyList())
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    val status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    private val sourceUris = mutableMapOf<Int, Uri>()
    private val sourceFiles = mutableMapOf<Int, File>()
    private val renderers = mutableMapOf<Int, PdfRenderer>()
    private val rendererMutex = Mutex()
    private var nextItemId = 0L
    private var nextDocKey = 0

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Idle
            selection.value = emptySet()
            try {
                closeAll()
                sourceUris.clear()
                sourceFiles.clear()
                nextDocKey = 0
                val docKey = registerDocument(uri) ?: throw IllegalStateException()
                val (name, _) = FileUtils.queryMeta(container.app, uri)
                val pageCount = pageCountOf(docKey)
                items.value = (0 until pageCount).map { index ->
                    PageItem(nextItemId++, docKey, index, rotation = 0)
                }
                source.value = Source(name)
            } catch (_: Exception) {
                source.value = null
                items.value = emptyList()
            }
        }
    }

    /** Ajoute les pages d'un autre PDF à la fin (ou après la sélection). */
    fun addPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docKey = registerDocument(uri) ?: return@launch
                val pageCount = pageCountOf(docKey)
                val added = (0 until pageCount).map { index ->
                    PageItem(nextItemId++, docKey, index, rotation = 0)
                }
                items.value = insertAfterSelection(added)
            } catch (_: Exception) {
            }
        }
    }

    fun insertBlankPage() {
        val blank = PageItem(nextItemId++, PageToolbox.BLANK_DOC_KEY, 0, rotation = 0)
        items.value = insertAfterSelection(listOf(blank))
    }

    private fun insertAfterSelection(added: List<PageItem>): List<PageItem> {
        val current = items.value
        val selectedIds = selection.value
        val lastSelected = current.indexOfLast { it.id in selectedIds }
        val position = if (lastSelected >= 0) lastSelected + 1 else current.size
        return current.subList(0, position) + added +
            current.subList(position, current.size)
    }

    fun toggleSelection(id: Long) {
        selection.value =
            if (id in selection.value) selection.value - id else selection.value + id
    }

    fun rotateSelected() {
        val selectedIds = selection.value
        if (selectedIds.isEmpty()) return
        items.value = items.value.map { item ->
            if (item.id in selectedIds) item.copy(rotation = (item.rotation + 90) % 360)
            else item
        }
    }

    fun duplicateSelected() {
        val selectedIds = selection.value
        if (selectedIds.isEmpty()) return
        val result = ArrayList<PageItem>(items.value.size + selectedIds.size)
        for (item in items.value) {
            result.add(item)
            if (item.id in selectedIds) {
                result.add(item.copy(id = nextItemId++))
            }
        }
        items.value = result
        selection.value = emptySet()
    }

    fun deleteSelected() {
        val selectedIds = selection.value
        if (selectedIds.isEmpty()) return
        val remaining = items.value.filterNot { it.id in selectedIds }
        if (remaining.isEmpty()) return // jamais un document vide
        items.value = remaining
        selection.value = emptySet()
    }

    /** Déplace la sélection d'une position ([offset] = -1 vers le haut, +1 vers le bas). */
    fun moveSelected(offset: Int) {
        val selectedIds = selection.value
        if (selectedIds.isEmpty()) return
        val list = items.value.toMutableList()
        val indices = list.withIndex()
            .filter { it.value.id in selectedIds }
            .map { it.index }
        val ordered = if (offset < 0) indices else indices.reversed()
        for (index in ordered) {
            val target = index + offset
            if (target < 0 || target >= list.size) return
            if (list[target].id in selectedIds) continue
            val moved = list.removeAt(index)
            list.add(target, moved)
        }
        items.value = list
    }

    fun outputName(): String =
        FileNameValidator.sanitize("pages_" + (source.value?.name ?: "document.pdf"))

    fun save(output: Uri) {
        val specs = items.value.map {
            PageToolbox.PageSpec(it.docKey, it.pageIndex, it.rotation)
        }
        if (specs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Working
            status.value = try {
                PageToolbox.rebuild(container.app, sourceUris.toMap(), specs, output)
                ToolStatus.Done(output, outputName())
            } catch (_: Exception) {
                ToolStatus.Error
            } catch (_: OutOfMemoryError) {
                ToolStatus.Error
            }
        }
    }

    fun shareResult() {
        val done = status.value as? ToolStatus.Done ?: return
        viewModelScope.launch(Dispatchers.IO) {
            FileUtils.shareDocument(container.app, done.output, done.outputName)
        }
    }

    /** Miniature d'une page (blanc uni pour les pages blanches insérées). */
    suspend fun thumbnail(item: PageItem): Bitmap? = withContext(Dispatchers.IO) {
        if (item.docKey == PageToolbox.BLANK_DOC_KEY) {
            return@withContext Bitmap.createBitmap(140, 198, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(AndroidColor.WHITE) }
        }
        rendererMutex.withLock {
            val renderer = renderers.getOrPut(item.docKey) {
                val file = sourceFiles[item.docKey] ?: return@withLock null
                PdfRenderer(
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                )
            }
            if (item.pageIndex < 0 || item.pageIndex >= renderer.pageCount) {
                return@withLock null
            }
            try {
                val page = renderer.openPage(item.pageIndex)
                try {
                    val targetWidth = 280
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        targetWidth, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    private fun registerDocument(uri: Uri): Int? {
        val file = FileUtils.copyToCache(container.app, uri, dirName = "pages") ?: return null
        val docKey = nextDocKey++
        sourceUris[docKey] = uri
        sourceFiles[docKey] = file
        return docKey
    }

    private fun pageCountOf(docKey: Int): Int {
        val file = sourceFiles[docKey] ?: return 0
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer -> return renderer.pageCount }
    }

    private fun closeAll() {
        for (renderer in renderers.values) {
            try {
                renderer.close()
            } catch (_: Exception) {
            }
        }
        renderers.clear()
    }

    override fun onCleared() {
        super.onCleared()
        closeAll()
        for (file in sourceFiles.values) file.delete()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { PagesViewModel(it) }
    val source by viewModel.source.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onPdfPicked(uri) }

    val addPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.addPdf(uri) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.save(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { createOutput.launch(viewModel.outputName()) }) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(R.string.save)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Column {
                    val hasSelection = selection.isNotEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { viewModel.rotateSelected() },
                            enabled = hasSelection
                        ) {
                            Icon(
                                Icons.Default.RotateRight,
                                contentDescription = stringResource(R.string.pages_rotate)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.duplicateSelected() },
                            enabled = hasSelection
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.pages_duplicate)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteSelected() },
                            enabled = hasSelection && selection.size < items.size
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.pages_delete)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveSelected(-1) },
                            enabled = hasSelection
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.pages_move_up)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveSelected(1) },
                            enabled = hasSelection
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.pages_move_down)
                            )
                        }
                        IconButton(onClick = { viewModel.insertBlankPage() }) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = stringResource(R.string.pages_insert_blank)
                            )
                        }
                        IconButton(onClick = { addPdf.launch(arrayOf("application/pdf")) }) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = stringResource(R.string.pages_insert_pdf)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.pages_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (source == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pages_instructions),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Text(
                            text = stringResource(R.string.choose_pdf),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            } else {
                source?.let { current ->
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                when (val currentStatus = status) {
                    is ToolStatus.Working -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.working),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    is ToolStatus.Done -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.pages_done),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.shareResult() }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share)
                                )
                            }
                        }
                    }

                    is ToolStatus.Error -> {
                        Text(
                            text = stringResource(R.string.tool_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    is ToolStatus.Idle -> Unit
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        PageCell(
                            item = item,
                            position = items.indexOf(item) + 1,
                            selected = item.id in selection,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCell(
    item: PagesViewModel.PageItem,
    position: Int,
    selected: Boolean,
    viewModel: PagesViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.toggleSelection(item.id) }
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val thumbnail by produceState<Bitmap?>(
                initialValue = null, item.docKey, item.pageIndex
            ) {
                value = viewModel.thumbnail(item)
            }
            val rendered = thumbnail
            if (rendered != null) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            rendered.width.toFloat() / rendered.height.toFloat()
                        )
                        .rotate(item.rotation.toFloat())
                        .background(androidx.compose.ui.graphics.Color.White)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            Text(
                text = position.toString() +
                    if (item.rotation != 0) " · ${item.rotation}°" else "",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
