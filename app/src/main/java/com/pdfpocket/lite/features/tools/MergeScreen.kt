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

class MergeViewModel(private val container: AppContainer) : ViewModel() {

    data class Item(val uri: Uri, val name: String, val pageCount: Int)

    val items = MutableStateFlow<List<Item>>(emptyList())
    val status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = uris.mapNotNull { uri ->
                try {
                    val (name, _) = FileUtils.queryMeta(container.app, uri)
                    val pages = PdfToolbox.pageCount(container.app, uri)
                    Item(uri, name, pages)
                } catch (_: Exception) {
                    null
                }
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

    fun merge(output: Uri, outputName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Working
            status.value = try {
                PdfToolbox.merge(container.app, items.value.map { it.uri }, output)
                ToolStatus.Done(output, outputName)
            } catch (_: Exception) {
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
fun MergeScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { MergeViewModel(it) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickPdfs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addUris(uris) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.merge(uri, "fusion.pdf") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_merge)) },
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
                    text = stringResource(R.string.merge_instructions),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                OutlinedButton(onClick = { pickPdfs.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.add_pdfs),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.pages_count, item.pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                text = stringResource(R.string.merge_done),
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
                            onClick = { createOutput.launch("fusion.pdf") },
                            enabled = items.size >= 2
                        ) {
                            Text(stringResource(R.string.merge_now))
                        }
                    }
                }
            }
        }
    }
}
