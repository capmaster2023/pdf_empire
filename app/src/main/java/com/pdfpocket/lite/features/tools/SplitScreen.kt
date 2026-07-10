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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.pdfpocket.lite.core.FileNameValidator
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.PageRangeParser
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.pdf.PdfToolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SplitViewModel(private val container: AppContainer) : ViewModel() {

    data class Source(val uri: Uri, val name: String, val pageCount: Int)

    val source = MutableStateFlow<Source?>(null)
    val rangeInput = MutableStateFlow("")
    val status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            source.value = try {
                val (name, _) = FileUtils.queryMeta(container.app, uri)
                val pages = PdfToolbox.pageCount(container.app, uri)
                Source(uri, name, pages)
            } catch (_: Exception) {
                null
            }
            status.value = ToolStatus.Idle
        }
    }

    fun parsedPages(): List<Int>? {
        val current = source.value ?: return null
        return PageRangeParser.parse(rangeInput.value, current.pageCount)
    }

    fun extract(output: Uri) {
        val current = source.value ?: return
        val pages = parsedPages() ?: return
        val outputName = FileNameValidator.sanitize("extrait_" + current.name)
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Working
            status.value = try {
                PdfToolbox.extractPages(container.app, current.uri, pages, output)
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
fun SplitScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SplitViewModel(it) }
    val source by viewModel.source.collectAsStateWithLifecycle()
    val rangeInput by viewModel.rangeInput.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onPdfPicked(uri) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.extract(uri) }

    val rangeValid = viewModel.parsedPages() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_extract_pages)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.split_instructions),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Text(
                    text = stringResource(R.string.choose_pdf),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            source?.let { current ->
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.pages_count, current.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rangeInput,
                    onValueChange = { viewModel.rangeInput.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.page_range_label)) },
                    supportingText = { Text(stringResource(R.string.page_range_example)) },
                    isError = rangeInput.isNotBlank() && !rangeValid
                )

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
                        Text(
                            text = stringResource(R.string.split_done),
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

                    is ToolStatus.Error -> {
                        Text(
                            text = stringResource(R.string.tool_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is ToolStatus.Idle -> {
                        Button(
                            onClick = {
                                createOutput.launch(
                                    FileNameValidator.sanitize("extrait_" + current.name)
                                )
                            },
                            enabled = rangeValid
                        ) {
                            Text(stringResource(R.string.extract_now))
                        }
                    }
                }
            }
        }
    }
}
