package com.pdfpocket.lite.features.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.pdf.PdfToolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ImagesToPdfViewModel(private val container: AppContainer) : ViewModel() {

    data class Item(val uri: Uri, val name: String)

    val items = MutableStateFlow<List<Item>>(emptyList())
    val status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = uris.map { uri ->
                val (name, _) = FileUtils.queryMeta(container.app, uri)
                Item(uri, name)
            }
            items.value = items.value + added
        }
    }

    fun move(index: Int, delta: Int) {
        val list = items.value.toMutableList()
        val target = index + delta
        if (index in list.indices && target in list.indices) {
            val tmp = list[index]
            list[index] = list[target]
            list[target] = tmp
            items.value = list
        }
    }

    fun remove(index: Int) {
        val list = items.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            items.value = list
        }
    }

    fun convert(output: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Working
            status.value = try {
                PdfToolbox.imagesToPdf(container.app, items.value.map { it.uri }, output)
                ToolStatus.Done(output, "images.pdf")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ImagesToPdfViewModel(it) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addUris(uris) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.convert(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_images_to_pdf)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.images_instructions),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                OutlinedButton(onClick = { pickImages.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.add_images),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            items(items.size) { index ->
                val item = items[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (index + 1).toString() + ". " + item.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { viewModel.move(index, -1) }) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_up)
                            )
                        }
                        IconButton(onClick = { viewModel.move(index, 1) }) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_down)
                            )
                        }
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.remove)
                            )
                        }
                    }
                }
            }
            item {
                when (val currentStatus = status) {
                    is ToolStatus.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.working),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    is ToolStatus.Done -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.images_done),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(onClick = { viewModel.shareResult() }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.share),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    is ToolStatus.Error -> {
                        Text(
                            text = stringResource(R.string.tool_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is ToolStatus.Idle -> {
                        Button(
                            onClick = { createOutput.launch("images.pdf") },
                            enabled = items.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.convert_now))
                        }
                    }
                }
            }
        }
    }
}
