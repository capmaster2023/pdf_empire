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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.pdfpocket.lite.pdf.StampToolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WatermarkViewModel(private val container: AppContainer) : ViewModel() {

    data class Source(val uri: Uri, val name: String)

    val source = MutableStateFlow<Source?>(null)
    val watermarkText = MutableStateFlow("")
    val watermarkOpacity = MutableStateFlow(0.15f)
    val pageNumbers = MutableStateFlow(false)
    val headerText = MutableStateFlow("")
    val footerText = MutableStateFlow("")
    val status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Idle
            source.value = try {
                val (name, _) = FileUtils.queryMeta(container.app, uri)
                Source(uri, name)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun hasWork(): Boolean =
        watermarkText.value.isNotBlank() || pageNumbers.value ||
            headerText.value.isNotBlank() || footerText.value.isNotBlank()

    fun outputName(): String =
        FileNameValidator.sanitize("filigrane_" + (source.value?.name ?: "document.pdf"))

    fun apply(output: Uri) {
        val current = source.value ?: return
        val options = StampToolbox.Options(
            watermarkText = watermarkText.value.trim(),
            watermarkOpacity = watermarkOpacity.value,
            pageNumbers = pageNumbers.value,
            headerText = headerText.value.trim(),
            footerText = footerText.value.trim()
        )
        viewModelScope.launch(Dispatchers.IO) {
            status.value = ToolStatus.Working
            status.value = try {
                StampToolbox.apply(container.app, current.uri, options, output)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { WatermarkViewModel(it) }
    val source by viewModel.source.collectAsStateWithLifecycle()
    val watermarkText by viewModel.watermarkText.collectAsStateWithLifecycle()
    val watermarkOpacity by viewModel.watermarkOpacity.collectAsStateWithLifecycle()
    val pageNumbers by viewModel.pageNumbers.collectAsStateWithLifecycle()
    val headerText by viewModel.headerText.collectAsStateWithLifecycle()
    val footerText by viewModel.footerText.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onPdfPicked(uri) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.apply(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watermark_title)) },
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
                text = stringResource(R.string.watermark_instructions),
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

                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { viewModel.watermarkText.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.watermark_text_label)) }
                )

                if (watermarkText.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.watermark_opacity,
                            (watermarkOpacity * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = watermarkOpacity,
                        onValueChange = { viewModel.watermarkOpacity.value = it },
                        valueRange = 0.05f..0.5f
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = pageNumbers,
                        onCheckedChange = { viewModel.pageNumbers.value = it }
                    )
                    Text(stringResource(R.string.watermark_page_numbers))
                }

                OutlinedTextField(
                    value = headerText,
                    onValueChange = { viewModel.headerText.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.watermark_header_label)) }
                )
                OutlinedTextField(
                    value = footerText,
                    onValueChange = { viewModel.footerText.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.watermark_footer_label)) }
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
                            text = stringResource(R.string.watermark_done),
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
                            onClick = { createOutput.launch(viewModel.outputName()) },
                            enabled = viewModel.hasWork()
                        ) {
                            Text(stringResource(R.string.watermark_apply))
                        }
                    }
                }
            }
        }
    }
}
