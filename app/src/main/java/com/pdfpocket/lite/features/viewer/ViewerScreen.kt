package com.pdfpocket.lite.features.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfpocket.lite.R
import com.pdfpocket.lite.core.PrintUtils
import com.pdfpocket.lite.core.appViewModel
import com.pdfpocket.lite.pdf.LinkToolbox
import com.pdfpocket.lite.pdf.SearchToolbox
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
    val search by viewModel.search.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showGoToPage by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    // Défilement automatique vers l'occurrence courante.
    LaunchedEffect(search.currentIndex, search.matches) {
        val match = search.matches.getOrNull(search.currentIndex) ?: return@LaunchedEffect
        listState.animateScrollToItem(match.pageIndex)
    }

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
                        IconButton(onClick = {
                            searchMode = !searchMode
                            if (!searchMode) {
                                searchInput = ""
                                viewModel.clearSearch()
                            }
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.viewer_search)
                            )
                        }
                        IconButton(onClick = {
                            viewModel.documentFile()?.let { file ->
                                PrintUtils.printPdf(context, file, currentState.name)
                            }
                        }) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = stringResource(R.string.viewer_print)
                            )
                        }
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchMode) {
                            SearchBar(
                                input = searchInput,
                                onInputChange = { searchInput = it },
                                search = search,
                                onSearch = { viewModel.startSearch(searchInput) },
                                onPrevious = { viewModel.previousMatch() },
                                onNext = { viewModel.nextMatch() },
                                onCopy = {
                                    search.matches.getOrNull(search.currentIndex)?.let {
                                        clipboard.setText(AnnotatedString(it.snippet))
                                    }
                                },
                                onClose = {
                                    searchMode = false
                                    searchInput = ""
                                    viewModel.clearSearch()
                                }
                            )
                        }
                        PdfPages(
                            viewModel = viewModel,
                            pageCount = currentState.pageCount,
                            initialPage = currentState.lastPage,
                            listState = listState,
                            onGoToPage = { target ->
                                scope.launch { listState.animateScrollToItem(target) }
                            }
                        )
                    }

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
    listState: androidx.compose.foundation.lazy.LazyListState,
    onGoToPage: (Int) -> Unit
) {
    val links by viewModel.links.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
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
                val currentMatch = search.matches.getOrNull(search.currentIndex)
                PdfPageItem(
                    index = index,
                    targetWidth = targetWidth,
                    viewModel = viewModel,
                    links = links[index].orEmpty(),
                    highlights = search.matches.filter { it.pageIndex == index },
                    currentMatch = if (currentMatch?.pageIndex == index) currentMatch
                    else null,
                    onGoToPage = onGoToPage
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    targetWidth: Int,
    viewModel: ViewerViewModel,
    links: List<LinkToolbox.LinkArea>,
    highlights: List<SearchToolbox.Match>,
    currentMatch: SearchToolbox.Match?,
    onGoToPage: (Int) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, targetWidth) {
        value = viewModel.renderPage(index, targetWidth)
    }
    val rendered = bitmap
    if (rendered != null) {
        val context = LocalContext.current
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = stringResource(R.string.page_number, index + 1),
                modifier = Modifier.fillMaxSize()
            )
            // Surlignage des occurrences de recherche.
            for (match in highlights) {
                val isCurrent = match === currentMatch
                for (rect in match.rects) {
                    Box(
                        modifier = Modifier
                            .offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (rect.x * widthPx).toInt(),
                                    (rect.yTop * heightPx).toInt()
                                )
                            }
                            .size(
                                width = with(density) { (rect.width * widthPx).toDp() },
                                height = with(density) { (rect.height * heightPx).toDp() }
                            )
                            .background(
                                if (isCurrent) Color(0x80FF9800)
                                else Color(0x66FFEB3B)
                            )
                    )
                }
            }
            // Zones de liens cliquables (URL externes et liens internes).
            for (link in links) {
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (link.x * widthPx).toInt(),
                                (link.yTop * heightPx).toInt()
                            )
                        }
                        .size(
                            width = with(density) { (link.width * widthPx).toDp() },
                            height = with(density) { (link.height * heightPx).toDp() }
                        )
                        .clickable {
                            val url = link.url
                            val target = link.targetPage
                            if (url != null) {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                } catch (_: Exception) {
                                }
                            } else if (target != null) {
                                onGoToPage(target)
                            }
                        }
                )
            }
        }
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

@Composable
private fun SearchBar(
    input: String,
    onInputChange: (String) -> Unit,
    search: ViewerViewModel.SearchState,
    onSearch: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.viewer_search_hint)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                )
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (search.searching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                )
            } else if (search.matches.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.viewer_search_count,
                        search.currentIndex + 1,
                        search.matches.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.viewer_search_previous)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.viewer_search_next)
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.viewer_search_copy)
                    )
                }
            } else if (search.query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.viewer_search_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}
