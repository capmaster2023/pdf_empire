package com.pdfpocket.lite.features.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.data.db.DocumentEntity
import com.pdfpocket.lite.features.home.DocumentRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode { NAME, DATE, SIZE }

class FilesViewModel(private val container: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    val sortMode = MutableStateFlow(SortMode.DATE)

    val documents = combine(container.documents.all, query, sortMode) { list, q, sort ->
        val filtered = if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        when (sort) {
            SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortMode.DATE -> filtered.sortedByDescending { it.lastOpenedAt }
            SortMode.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            container.documents.setFavorite(document.uri, !document.isFavorite)
        }
    }

    fun removeFromHistory(document: DocumentEntity) {
        viewModelScope.launch {
            container.documents.removeFromHistory(document.uri)
        }
    }

    fun share(document: DocumentEntity) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            FileUtils.shareDocument(container.app, android.net.Uri.parse(document.uri), document.name)
        }
    }
}

@Composable
fun FilesScreen(onOpenViewer: (String) -> Unit) {
    val viewModel = appViewModel { FilesViewModel(it) }
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var sortMenuOpen by remember { mutableStateOf(false) }
    var documentToRemove by remember { mutableStateOf<DocumentEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort))
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by_name)) },
                    onClick = { viewModel.sortMode.value = SortMode.NAME; sortMenuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by_date)) },
                    onClick = { viewModel.sortMode.value = SortMode.DATE; sortMenuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by_size)) },
                    onClick = { viewModel.sortMode.value = SortMode.SIZE; sortMenuOpen = false }
                )
            }
        }

        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.empty_files),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents.size) { index ->
                    val document = documents[index]
                    Column {
                        DocumentRow(document = document, onClick = onOpenViewer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { viewModel.toggleFavorite(document) }) {
                                Icon(
                                    imageVector = if (document.isFavorite) Icons.Default.Star
                                    else Icons.Default.StarBorder,
                                    contentDescription = stringResource(R.string.favorite)
                                )
                            }
                            IconButton(onClick = { viewModel.share(document) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share)
                                )
                            }
                            IconButton(onClick = { documentToRemove = document }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.remove_from_history)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    documentToRemove?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToRemove = null },
            title = { Text(stringResource(R.string.remove_from_history)) },
            text = { Text(stringResource(R.string.remove_from_history_message, document.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromHistory(document)
                    documentToRemove = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { documentToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
