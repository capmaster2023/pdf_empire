package com.pdfpocket.lite.features.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.data.db.DocumentEntity
import com.pdfpocket.lite.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val recents = container.documents.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites = container.documents.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onPdfPicked(uri: Uri, onRegistered: (String) -> Unit) {
        viewModelScope.launch {
            container.documents.registerDocument(uri)
            onRegistered(uri.toString())
        }
    }
}

@Composable
fun HomeScreen(
    onOpenViewer: (String) -> Unit,
    onOpenTool: (String) -> Unit
) {
    val viewModel = appViewModel { HomeViewModel(it) }
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val openPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onPdfPicked(uri) { registered -> onOpenViewer(registered) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FileOpen,
                    label = stringResource(R.string.action_open_pdf),
                    onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Image,
                    label = stringResource(R.string.action_images_to_pdf),
                    onClick = { onOpenTool(Routes.IMAGES_TO_PDF) }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CallMerge,
                    label = stringResource(R.string.action_merge),
                    onClick = { onOpenTool(Routes.MERGE) }
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ContentCut,
                    label = stringResource(R.string.action_extract_pages),
                    onClick = { onOpenTool(Routes.SPLIT) }
                )
            }
        }

        item {
            QuickAction(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Draw,
                label = stringResource(R.string.fill_sign_title),
                onClick = { onOpenTool(Routes.FILL_SIGN) }
            )
        }

        if (favorites.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.section_favorites),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(favorites.size) { index ->
                DocumentRow(document = favorites[index], onClick = onOpenViewer)
            }
        }

        item {
            Text(
                text = stringResource(R.string.section_recents),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (recents.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = stringResource(R.string.empty_recents),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        } else {
            items(recents.size) { index ->
                DocumentRow(document = recents[index], onClick = onOpenViewer)
            }
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DocumentRow(
    document: DocumentEntity,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(document.uri) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildString {
                    append(FileUtils.formatSize(document.sizeBytes))
                    if (document.pageCount > 0) {
                        append(" · ")
                        append(
                            stringResource(R.string.pages_count, document.pageCount)
                        )
                    }
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (document.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.favorite),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
