package com.pdfpocket.lite.features.fillsign

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.features.tools.ToolStatus
import com.pdfpocket.lite.pdf.FieldType
import com.pdfpocket.lite.pdf.FormFieldInfo
import kotlin.math.roundToInt


private sealed interface SelectedPlacedElement {
    val id: Long

    data class Signature(override val id: Long) : SelectedPlacedElement
    data class FreeText(override val id: Long) : SelectedPlacedElement
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillSignScreen(
    initialUri: String?,
    onBack: () -> Unit
) {
    val viewModel = appViewModel { FillSignViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val textValues by viewModel.textValues.collectAsStateWithLifecycle()
    val checkValues by viewModel.checkValues.collectAsStateWithLifecycle()
    val choiceValues by viewModel.choiceValues.collectAsStateWithLifecycle()
    val pageSizes by viewModel.pageSizes.collectAsStateWithLifecycle()
    val placed by viewModel.placed.collectAsStateWithLifecycle()
    val placedTexts by viewModel.placedTexts.collectAsStateWithLifecycle()
    val placing by viewModel.placing.collectAsStateWithLifecycle()
    val signatureBitmap by viewModel.signatureBitmap.collectAsStateWithLifecycle()
    val initialsBitmap by viewModel.initialsBitmap.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val previewVersion by viewModel.previewVersion.collectAsStateWithLifecycle()
    val previewActive by viewModel.previewActive.collectAsStateWithLifecycle()
    val previewWorking by viewModel.previewWorking.collectAsStateWithLifecycle()

    var editingField by remember { mutableStateOf<FormFieldInfo?>(null) }
    var padTarget by remember { mutableStateOf<Boolean?>(null) } // false=signature, true=paraphe
    var showTextDialog by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<SelectedPlacedElement?>(null) }

    LaunchedEffect(placed, placedTexts, selectedElement) {
        selectedElement = when (val selected = selectedElement) {
            is SelectedPlacedElement.Signature ->
                selected.takeIf { placed.any { item -> item.id == selected.id } }
            is SelectedPlacedElement.FreeText ->
                selected.takeIf { placedTexts.any { item -> item.id == selected.id } }
            null -> null
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && state is FillSignViewModel.UiState.Empty) {
            viewModel.open(Uri.parse(initialUri))
        }
    }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.open(uri) }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.save(uri) }

    val currentState = state

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (currentState as? FillSignViewModel.UiState.Ready)?.name
                            ?: stringResource(R.string.fill_sign_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (currentState is FillSignViewModel.UiState.Ready &&
                        saveStatus !is ToolStatus.Working
                    ) {
                        IconButton(onClick = { createOutput.launch(viewModel.outputName()) }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.save_copy)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentState is FillSignViewModel.UiState.Ready) {
                BottomAppBar {
                    when {
                        placing != null -> {
                            Text(
                                text = stringResource(R.string.tap_to_place),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            )
                            TextButton(onClick = { viewModel.cancelPlacing() }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }

                        else -> {
                            TextButton(
                                onClick = {
                                    if (signatureBitmap == null) padTarget = false
                                    else viewModel.startPlacing(false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.add_signature),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (initialsBitmap == null) padTarget = true
                                    else viewModel.startPlacing(true)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Gesture, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.add_initials),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            TextButton(
                                onClick = { showTextDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.add_free_text),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentState) {
                is FillSignViewModel.UiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.fill_sign_intro),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_pdf),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                is FillSignViewModel.UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is FillSignViewModel.UiState.Error -> {
                    Text(
                        text = stringResource(R.string.error_unreadable_pdf),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                is FillSignViewModel.UiState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (previewWorking) {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (fields.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_form_fields),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        when (val status = saveStatus) {
                            is ToolStatus.Working -> {
                                Row(
                                    modifier = Modifier.padding(16.dp),
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
                                        text = stringResource(R.string.fill_sign_done),
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

                        if (placed.isNotEmpty() || placedTexts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.signature_drag_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 4.dp
                                )
                            )
                        }

                        when (val selected = selectedElement) {
                            is SelectedPlacedElement.Signature -> {
                                val item = placed.firstOrNull { it.id == selected.id }
                                if (item != null) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp, vertical = 4.dp
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.resize_signature),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Slider(
                                            value = item.placement.widthRatio,
                                            onValueChange = {
                                                viewModel.setSignatureSize(item.id, it)
                                            },
                                            valueRange = 0.03f..0.90f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            is SelectedPlacedElement.FreeText -> {
                                val item = placedTexts.firstOrNull { it.id == selected.id }
                                if (item != null) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp, vertical = 4.dp
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.resize_text),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Slider(
                                            value = item.heightRatio,
                                            onValueChange = {
                                                viewModel.setTextSize(item.id, it)
                                            },
                                            valueRange = 0.005f..0.08f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            null -> Unit
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentState.pageCount) { index ->
                                FillSignPage(
                                    index = index,
                                    viewModel = viewModel,
                                    renderVersion = previewVersion,
                                    previewActive = previewActive,
                                    pageSize = pageSizes.getOrNull(index),
                                    fields = fields.filter { it.pageIndex == index },
                                    textValues = textValues,
                                    checkValues = checkValues,
                                    choiceValues = choiceValues,
                                    placed = placed.filter { it.placement.pageIndex == index },
                                    placedTexts = placedTexts.filter { it.pageIndex == index },
                                    placing = placing != null,
                                    selectedSignatureId =
                                        (selectedElement as? SelectedPlacedElement.Signature)?.id,
                                    selectedTextId =
                                        (selectedElement as? SelectedPlacedElement.FreeText)?.id,
                                    onSelectSignature = { id ->
                                        selectedElement = SelectedPlacedElement.Signature(id)
                                    },
                                    onSelectText = { id ->
                                        selectedElement = SelectedPlacedElement.FreeText(id)
                                    },
                                    onTapField = { field ->
                                        when (field.type) {
                                            FieldType.CHECKBOX -> viewModel.toggleCheck(field.name)
                                            else -> editingField = field
                                        }
                                    },
                                    onPlace = { xRatio, yRatio ->
                                        viewModel.placeAt(index, xRatio, yRatio)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog d'édition d'un champ texte ou liste
    editingField?.let { field ->
        when (field.type) {
            FieldType.TEXT -> {
                var input by remember(field.name) {
                    mutableStateOf(textValues[field.name] ?: "")
                }
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text(stringResource(R.string.edit_field)) },
                    text = {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setTextValue(field.name, input)
                            editingField = null
                        }) { Text(stringResource(R.string.confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            FieldType.CHOICE -> {
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text(stringResource(R.string.choose_option)) },
                    text = {
                        Column {
                            field.options.forEach { option ->
                                TextButton(
                                    onClick = {
                                        viewModel.setChoice(field.name, option)
                                        editingField = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(option) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            else -> Unit
        }
    }

    // Pavé de dessin signature / paraphe
    padTarget?.let { isInitials ->
        SignaturePadDialog(
            title = stringResource(
                if (isInitials) R.string.add_initials else R.string.add_signature
            ),
            onDismiss = { padTarget = null },
            onConfirm = { bitmap: Bitmap ->
                viewModel.setSignature(bitmap, isInitials)
                padTarget = null
            }
        )
    }

    // Saisie d'un texte libre à poser sur la page
    if (showTextDialog) {
        var freeText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(stringResource(R.string.add_free_text)) },
            text = {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startPlacingText(freeText)
                        showTextDialog = false
                    },
                    enabled = freeText.isNotBlank()
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun FillSignPage(
    index: Int,
    viewModel: FillSignViewModel,
    renderVersion: Int,
    previewActive: Boolean,
    pageSize: FillSignViewModel.PageSize?,
    fields: List<FormFieldInfo>,
    textValues: Map<String, String>,
    checkValues: Map<String, Boolean>,
    choiceValues: Map<String, String>,
    placed: List<FillSignViewModel.PlacedSignature>,
    placedTexts: List<FillSignViewModel.PlacedText>,
    placing: Boolean,
    selectedSignatureId: Long?,
    selectedTextId: Long?,
    onSelectSignature: (Long) -> Unit,
    onSelectText: (Long) -> Unit,
    onTapField: (FormFieldInfo) -> Unit,
    onPlace: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidth = remember(configuration, density) {
        (configuration.screenWidthDp * density.density * 2)
            .toInt()
            .coerceIn(720, 2160)
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, index, targetWidth, renderVersion) {
        value = viewModel.renderPage(index, targetWidth)
    }

    val ratio = if (pageSize != null && pageSize.height > 0) {
        pageSize.width / pageSize.height
    } else 0.707f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color.White)
            .pointerInput(placing) {
                if (placing) {
                    detectTapGestures { offset ->
                        onPlace(
                            offset.x / size.width.coerceAtLeast(1),
                            offset.y / size.height.coerceAtLeast(1)
                        )
                    }
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val rendered = bitmap
        if (rendered != null) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(R.string.page_number, index + 1),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Champs de formulaire cliquables
        if (pageSize != null && pageSize.width > 0) {
            val scale = widthPx / pageSize.width
            fields.forEach { field ->
                val xPx = (field.x * scale).roundToInt()
                val yPx = (field.yTop * scale).roundToInt()
                val fieldWidth = (field.width * scale).roundToInt().coerceAtLeast(8)
                val fieldHeight = (field.height * scale).roundToInt().coerceAtLeast(8)
                val label = when (field.type) {
                    FieldType.TEXT -> textValues[field.name] ?: ""
                    FieldType.CHOICE -> choiceValues[field.name] ?: ""
                    FieldType.CHECKBOX ->
                        if (checkValues[field.name] == true) "\u2713" else ""
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx, yPx) }
                        .size(
                            width = with(density) { fieldWidth.toDp() },
                            height = with(density) { fieldHeight.toDp() }
                        )
                        .background(
                            if (previewActive) Color(0x142F6FDE) else Color(0x332F6FDE)
                        )
                        .border(1.dp, Color(0xFF2F6FDE))
                        .clickable { onTapField(field) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (!previewActive && label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        // Signatures placées : glisser pour déplacer, pincer pour redimensionner,
        // appui long pour supprimer.
        placed.forEach { item ->
            val signatureWidth = item.placement.widthRatio * widthPx
            val signatureHeight =
                signatureWidth * item.bitmap.height / item.bitmap.width.coerceAtLeast(1)
            Image(
                bitmap = item.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (item.placement.xRatio * widthPx).roundToInt(),
                            (item.placement.yTopRatio * heightPx).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { signatureWidth.toDp() },
                        height = with(density) { signatureHeight.toDp() }
                    )
                    .then(
                        if (selectedSignatureId == item.id) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onSelectSignature(item.id) },
                            onLongPress = { viewModel.removeSignature(item.id) }
                        )
                    }
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onSelectSignature(item.id)
                            viewModel.transformPlaced(
                                id = item.id,
                                dxRatio = pan.x / widthPx.coerceAtLeast(1f),
                                dyRatio = pan.y / heightPx.coerceAtLeast(1f),
                                zoom = zoom
                            )
                        }
                    }
            )
        }

        // Textes libres : mêmes gestes que les signatures.
        placedTexts.forEach { item ->
            val fontSizePx = item.heightRatio * heightPx
            Text(
                text = item.text,
                color = Color.Black,
                fontSize = with(density) { fontSizePx.toSp() },
                lineHeight = with(density) { (fontSizePx * 1.2f).toSp() },
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (item.xRatio * widthPx).roundToInt(),
                            (item.yTopRatio * heightPx).roundToInt()
                        )
                    }
                    .then(
                        if (selectedTextId == item.id) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onSelectText(item.id) },
                            onLongPress = { viewModel.removeText(item.id) }
                        )
                    }
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onSelectText(item.id)
                            viewModel.transformText(
                                id = item.id,
                                dxRatio = pan.x / widthPx.coerceAtLeast(1f),
                                dyRatio = pan.y / heightPx.coerceAtLeast(1f),
                                zoom = zoom
                            )
                        }
                    }
            )
        }
    }
}
