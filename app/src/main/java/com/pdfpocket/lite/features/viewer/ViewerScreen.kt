package com.pdfpocket.lite.features.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.appViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ViewerScreen(
    uriString: String,
    onBack: () -> Unit,
    onFillSign: () -> Unit
) {
    val viewModel = appViewModel { ViewerViewModel(it, uriString) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showGoToPage by remember { mutableStateOf(false) }

    val currentState = state

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (currentState as? ViewerViewModel.UiState.Ready)?.name
                            ?: stringResource(R.string.viewer_title),
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
                    if (currentState is ViewerViewModel.UiState.Ready) {
                        IconButton(onClick = onFillSign) {
                            Icon(
                                Icons.Default.Draw,
                                contentDescription = stringResource(R.string.fill_sign_title)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star
                                else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.favorite)
                            )
                        }
                        IconButton(onClick = { viewModel.share(currentState.name) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentState is ViewerViewModel.UiState.Ready) {
                val currentPage = listState.firstVisibleItemIndex + 1
                TextButton(
                    onClick = { showGoToPage = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.page_indicator, currentPage, currentState.pageCount
                        )
                    )
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
                is ViewerViewModel.UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ViewerViewModel.UiState.Error -> {
                    val message = when (currentState.type) {
                        ViewerViewModel.ErrorType.PROTECTED ->
                            stringResource(R.string.error_protected_pdf)

                        ViewerViewModel.ErrorType.UNREADABLE ->
                            stringResource(R.string.error_unreadable_pdf)
                    }
                    Text(
                        text = message,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is ViewerViewModel.UiState.Ready -> {
                    PdfPages(
                        viewModel = viewModel,
                        pageCount = currentState.pageCount,
                        initialPage = currentState.lastPage,
                        listState = listState
                    )

                    LaunchedEffect(Unit) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .debounce(400)
                            .collect { viewModel.onPageVisible(it) }
                    }
                }
            }
        }
    }

    if (showGoToPage && currentState is ViewerViewModel.UiState.Ready) {
        var pageInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToPage = false },
            title = { Text(stringResource(R.string.go_to_page)) },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(
                                R.string.go_to_page_hint, currentState.pageCount
                            )
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = pageInput.trim().toIntOrNull()
                    if (target != null && target in 1..currentState.pageCount) {
                        scope.launch { listState.scrollToItem(target - 1) }
                        showGoToPage = false
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPage = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PdfPages(
    viewModel: ViewerViewModel,
    pageCount: Int,
    initialPage: Int,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidth = remember(configuration, density) {
        (configuration.screenWidthDp * density.density * 2)
            .toInt()
            .coerceIn(720, 2160)
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pageCount) {
        if (initialPage in 1 until pageCount) {
            listState.scrollToItem(initialPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (zoom > 1f) {
                        zoom = 1f; offsetX = 0f; offsetY = 0f
                    } else {
                        zoom = 2f
                    }
                })
            }
            .pointerInput(Unit) {
                // Pincement à deux doigts : zoom + déplacement.
                // Un doigt : le défilement de la liste reste natif.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            if (zoom > 1f) {
                                val maxX = size.width * (zoom - 1f) / 2f
                                val maxY = size.height * (zoom - 1f) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f; offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offsetX
                    translationY = offsetY
                },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { index ->
                PdfPageItem(
                    index = index,
                    targetWidth = targetWidth,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    targetWidth: Int,
    viewModel: ViewerViewModel
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, targetWidth) {
        value = viewModel.renderPage(index, targetWidth)
    }
    val rendered = bitmap
    if (rendered != null) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = stringResource(R.string.page_number, index + 1),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                .background(MaterialTheme.colorScheme.surface)
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
}
