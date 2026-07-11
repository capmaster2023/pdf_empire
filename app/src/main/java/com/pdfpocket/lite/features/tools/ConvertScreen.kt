package com.pdfpocket.lite.features.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
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
import com.pdfpocket.lite.core.FileNameValidator
import com.pdfpocket.lite.core.FileUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.pdf.ConvertToolbox
import com.pdfpocket.lite.pdf.OfficeExportToolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ConvertViewModel(private val container: AppContainer) : ViewModel() {

    /** Résultat d'une conversion : nombre d'éléments pour les exports galerie. */
    sealed interface ConvertStatus {
        data object Idle : ConvertStatus
        data object Working : ConvertStatus
        data class DoneGallery(val count: Int) : ConvertStatus
        data object DoneFile : ConvertStatus
        data object Error : ConvertStatus
    }

    data class Source(val uri: Uri, val name: String)

    val source = MutableStateFlow<Source?>(null)
    val status = MutableStateFlow<ConvertStatus>(ConvertStatus.Idle)

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ConvertStatus.Idle
            source.value = try {
                val (name, _) = FileUtils.queryMeta(container.app, uri)
                Source(uri, name)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun baseName(): String =
        (source.value?.name ?: "document.pdf").substringBeforeLast('.')

    fun sanitizedName(suffix: String): String =
        FileNameValidator.sanitize(baseName() + suffix)

    fun toImages() = runGallery { current ->
        ConvertToolbox.pdfToImages(container.app, current.uri, baseName())
    }

    fun extractImages() = runGallery { current ->
        ConvertToolbox.extractImages(container.app, current.uri, baseName())
    }

    fun toText(output: Uri) = runFile { current ->
        ConvertToolbox.pdfToText(container.app, current.uri, output)
    }

    fun toDocx(output: Uri) = runFile { current ->
        OfficeExportToolbox.pdfToDocx(container.app, current.uri, output)
    }

    fun toRtf(output: Uri) = runFile { current ->
        OfficeExportToolbox.pdfToRtf(container.app, current.uri, output)
    }

    fun toXlsx(output: Uri) = runFile { current ->
        OfficeExportToolbox.pdfToXlsx(container.app, current.uri, output)
    }

    fun toPptx(output: Uri) = runFile { current ->
        OfficeExportToolbox.pdfToPptx(container.app, current.uri, output)
    }

    private fun runGallery(block: (Source) -> Int) {
        val current = source.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ConvertStatus.Working
            status.value = try {
                ConvertStatus.DoneGallery(block(current))
            } catch (_: Exception) {
                ConvertStatus.Error
            } catch (_: OutOfMemoryError) {
                ConvertStatus.Error
            }
        }
    }

    private fun runFile(block: (Source) -> Unit) {
        val current = source.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ConvertStatus.Working
            status.value = try {
                block(current)
                ConvertStatus.DoneFile
            } catch (_: Exception) {
                ConvertStatus.Error
            } catch (_: OutOfMemoryError) {
                ConvertStatus.Error
            }
        }
    }
}

private const val MIME_DOCX =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_XLSX =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val MIME_PPTX =
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ConvertViewModel(it) }
    val source by viewModel.source.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onPdfPicked(uri) }

    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) viewModel.toText(uri) }
    val createDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_DOCX)
    ) { uri -> if (uri != null) viewModel.toDocx(uri) }
    val createRtf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/rtf")
    ) { uri -> if (uri != null) viewModel.toRtf(uri) }
    val createXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_XLSX)
    ) { uri -> if (uri != null) viewModel.toXlsx(uri) }
    val createPptx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_PPTX)
    ) { uri -> if (uri != null) viewModel.toPptx(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.convert_title)) },
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
                text = stringResource(R.string.convert_instructions),
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

                when (val currentStatus = status) {
                    is ConvertViewModel.ConvertStatus.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.working),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    is ConvertViewModel.ConvertStatus.DoneGallery -> {
                        Text(
                            text = stringResource(
                                R.string.convert_done_gallery, currentStatus.count
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is ConvertViewModel.ConvertStatus.DoneFile -> {
                        Text(
                            text = stringResource(R.string.convert_done_file),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is ConvertViewModel.ConvertStatus.Error -> {
                        Text(
                            text = stringResource(R.string.tool_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is ConvertViewModel.ConvertStatus.Idle -> Unit
                }

                val working = status is ConvertViewModel.ConvertStatus.Working

                Text(
                    text = stringResource(R.string.convert_section_media),
                    style = MaterialTheme.typography.titleSmall
                )
                Button(
                    onClick = { viewModel.toImages() },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_images)) }
                Button(
                    onClick = { viewModel.extractImages() },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_extract_images)) }
                Button(
                    onClick = { createText.launch(viewModel.sanitizedName(".txt")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_text)) }

                Text(
                    text = stringResource(R.string.convert_section_office),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.convert_office_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { createDocx.launch(viewModel.sanitizedName(".docx")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_docx)) }
                Button(
                    onClick = { createRtf.launch(viewModel.sanitizedName(".rtf")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_rtf)) }
                Button(
                    onClick = { createXlsx.launch(viewModel.sanitizedName(".xlsx")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_xlsx)) }
                Button(
                    onClick = { createPptx.launch(viewModel.sanitizedName(".pptx")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.convert_to_pptx)) }
            }
        }
    }
}
