package com.paperwise.ui.viewer

import android.graphics.Bitmap
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private data class AcrobatChrome(
    val workspace: Color,
    val topBar: Color,
    val onTopBar: Color,
    val topBarSubtle: Color,
    val controlSurface: Color,
    val onControlSurface: Color,
    val pageCard: Color
)

private enum class ViewMode {
    SINGLE,
    CONTINUOUS
}

private enum class DisplayMode {
    LIGHT,
    DARK,
    SEPIA,
    READING
}

/**
 * PDF Viewer screen with zoom, pan, and page navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val isDarkTheme = isSystemInDarkTheme()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val zoomLevel by viewModel.zoomLevel.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPagesState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isCurrentPageBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var caseSensitiveSearch by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var jumpToPageInput by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(ViewMode.CONTINUOUS) }
    var displayMode by remember {
        mutableStateOf(if (isDarkTheme) DisplayMode.DARK else DisplayMode.LIGHT)
    }
    val chrome = remember(displayMode, isDarkTheme) {
        when (displayMode) {
            DisplayMode.DARK -> AcrobatChrome(
                workspace = Color(0xFF1A1A1A),
                topBar = Color(0xFF151515),
                onTopBar = Color(0xFFFFFFFF),
                topBarSubtle = Color(0xFFBDBDBD),
                controlSurface = Color(0xFF202020),
                onControlSurface = Color(0xFFFFFFFF),
                pageCard = Color(0xFF2B2B2B)
            )
            DisplayMode.SEPIA -> AcrobatChrome(
                workspace = Color(0xFFF2E9D7),
                topBar = Color(0xFF4B3B26),
                onTopBar = Color(0xFFF8EEDB),
                topBarSubtle = Color(0xFFE5D8BF),
                controlSurface = Color(0xFF5A4730),
                onControlSurface = Color(0xFFF8EEDB),
                pageCard = Color(0xFFFFF9EF)
            )
            DisplayMode.LIGHT, DisplayMode.READING -> {
                if (isDarkTheme) {
                    AcrobatChrome(
                        workspace = Color(0xFF1C1C1C),
                        topBar = Color(0xFF242424),
                        onTopBar = Color(0xFFFFFFFF),
                        topBarSubtle = Color(0xFFCDCDCD),
                        controlSurface = Color(0xFF2B2B2B),
                        onControlSurface = Color(0xFFFFFFFF),
                        pageCard = Color(0xFFFFFFFF)
                    )
                } else {
                    AcrobatChrome(
                        workspace = Color(0xFFF2F2F2),
                        topBar = Color(0xFF2A2A2A),
                        onTopBar = Color(0xFFFFFFFF),
                        topBarSubtle = Color(0xFFD9D9D9),
                        controlSurface = Color(0xFF2F2F2F),
                        onControlSurface = Color(0xFFFFFFFF),
                        pageCard = Color(0xFFFFFFFF)
                    )
                }
            }
        }
    }
    val documentLabel = remember(filePath) {
        val raw = runCatching { filePath.toUri().lastPathSegment }.getOrNull()
            ?: filePath.substringAfterLast('/')
        val decoded = runCatching {
            URLDecoder.decode(raw ?: "", StandardCharsets.UTF_8.name())
        }.getOrDefault(raw ?: "")

        decoded
            .substringAfterLast('/')
            .substringAfterLast(':')
            .ifBlank { "Document" }
    }
    
    LaunchedEffect(filePath) {
        viewModel.loadDocument(filePath)
    }
    
    Scaffold(
        containerColor = chrome.workspace,
        topBar = {
            if (displayMode != DisplayMode.READING) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chrome.topBar,
                    titleContentColor = chrome.onTopBar,
                    navigationIconContentColor = chrome.onTopBar,
                    actionIconContentColor = chrome.onTopBar
                ),
                title = {
                    when (uiState) {
                        is PdfViewerUiState.Success -> {
                            val state = uiState as PdfViewerUiState.Success
                            Column {
                                Text(
                                    text = documentLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page ${state.currentPage + 1} of ${state.totalPages}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = chrome.topBarSubtle
                                )
                            }
                        }
                        else -> Text("PDF Viewer")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSearchDialog = true },
                        enabled = uiState is PdfViewerUiState.Success
                    ) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            enabled = uiState is PdfViewerUiState.Success
                        ) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh Page") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.reloadCurrentPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Zoom") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.setZoom(1f)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Go To First Page") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.goToFirstPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Jump To Page") },
                                onClick = {
                                    showMoreMenu = false
                                    showJumpToPageDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks") },
                                onClick = {
                                    showMoreMenu = false
                                    showBookmarksSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (viewMode == ViewMode.SINGLE) {
                                            "Switch To Continuous"
                                        } else {
                                            "Switch To Single Page"
                                        }
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    viewMode = if (viewMode == ViewMode.SINGLE) {
                                        ViewMode.CONTINUOUS
                                    } else {
                                        ViewMode.SINGLE
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Display: Light") },
                                onClick = {
                                    showMoreMenu = false
                                    displayMode = DisplayMode.LIGHT
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Display: Dark") },
                                onClick = {
                                    showMoreMenu = false
                                    displayMode = DisplayMode.DARK
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Display: Sepia") },
                                onClick = {
                                    showMoreMenu = false
                                    displayMode = DisplayMode.SEPIA
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Display: Reading") },
                                onClick = {
                                    showMoreMenu = false
                                    displayMode = DisplayMode.READING
                                }
                            )
                        }
                    }
                }
            )
            }
        },
        bottomBar = {
            val successState = uiState as? PdfViewerUiState.Success
            if (successState != null && displayMode != DisplayMode.READING) {
                AcrobatBottomToolbar(
                    currentPage = currentPage,
                    totalPages = successState.totalPages,
                    currentZoom = zoomLevel,
                    isCurrentPageBookmarked = isCurrentPageBookmarked,
                    chrome = chrome,
                    onPrevious = {
                        if (viewMode == ViewMode.SINGLE) {
                            viewModel.previousPage()
                        } else {
                            viewModel.setCurrentPage(currentPage - 1)
                        }
                    },
                    onNext = {
                        if (viewMode == ViewMode.SINGLE) {
                            viewModel.nextPage()
                        } else {
                            viewModel.setCurrentPage(currentPage + 1)
                        }
                    },
                    onZoomOut = {
                        val target = (zoomLevel - 0.1f).coerceAtLeast(0.5f)
                        viewModel.setZoom(target, reloadCurrentPage = false)
                    },
                    onZoomIn = {
                        val target = (zoomLevel + 0.1f).coerceAtMost(3.0f)
                        viewModel.setZoom(target, reloadCurrentPage = false)
                    },
                    onToggleBookmark = {
                        scope.launch {
                            val result = viewModel.toggleBookmarkForCurrentPage()
                            val message = result.fold(
                                onSuccess = { added ->
                                    if (added) "Bookmark added." else "Bookmark removed."
                                },
                                onFailure = { error ->
                                    error.message ?: "Failed to update bookmark."
                                }
                            )
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is PdfViewerUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PdfViewerUiState.Error -> {
                    ErrorView(
                        message = (uiState as PdfViewerUiState.Error).message,
                        onRetry = { viewModel.loadDocument(filePath) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PdfViewerUiState.Success -> {
                    val bitmap = (uiState as PdfViewerUiState.Success).bitmap
                    if (viewMode == ViewMode.SINGLE) {
                        PdfPageView(
                            bitmap = bitmap,
                            zoomLevel = zoomLevel,
                            displayMode = displayMode,
                            onZoomChange = { viewModel.setZoom(it, reloadCurrentPage = false) },
                            onPreviousPage = viewModel::previousPage,
                            onNextPage = viewModel::nextPage,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ContinuousPdfView(
                            totalPages = totalPages,
                            currentZoom = zoomLevel,
                            displayMode = displayMode,
                            chrome = chrome,
                            currentPage = currentPage,
                            onZoomChange = { viewModel.setZoom(it, reloadCurrentPage = false) },
                            onPageVisible = viewModel::setCurrentPage,
                            loadBitmap = { page, zoom -> viewModel.loadPageBitmap(page, zoom) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (searchResults.isNotEmpty() && currentSearchIndex in searchResults.indices) {
                val result = searchResults[currentSearchIndex]
                SearchResultBar(
                    resultText = result.matchText,
                    currentIndex = currentSearchIndex + 1,
                    total = searchResults.size,
                    onPrevious = viewModel::previousSearchResult,
                    onNext = viewModel::nextSearchResult,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                )
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search In Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search text or page number") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Case-sensitive")
                        Switch(
                            checked = caseSensitiveSearch,
                            onCheckedChange = { caseSensitiveSearch = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val query = searchQuery.trim()
                        showSearchDialog = false
                        scope.launch {
                            val pageJump = query.toIntOrNull()
                            if (pageJump != null) {
                                val page = pageJump - 1
                                viewModel.goToPage(page)
                                snackbarHostState.showSnackbar("Moved to page $pageJump")
                            } else {
                                val count = runCatching {
                                    viewModel.performSearch(query, caseSensitiveSearch)
                                }.getOrDefault(0)
                                if (count > 0) {
                                    snackbarHostState.showSnackbar("$count matches found.")
                                } else {
                                    snackbarHostState.showSnackbar("No matches found.")
                                }
                            }
                        }
                    },
                    enabled = searchQuery.isNotBlank()
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showJumpToPageDialog) {
        val totalPages = (uiState as? PdfViewerUiState.Success)?.totalPages ?: 0
        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Jump To Page") },
            text = {
                OutlinedTextField(
                    value = jumpToPageInput,
                    onValueChange = { value ->
                        jumpToPageInput = value.filter { it.isDigit() }
                    },
                    label = { Text("Page number (1-$totalPages)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val page = jumpToPageInput.toIntOrNull()
                        showJumpToPageDialog = false
                        if (page != null && page in 1..totalPages) {
                            viewModel.goToPage(page - 1)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Invalid page number.")
                            }
                        }
                        jumpToPageInput = ""
                    },
                    enabled = jumpToPageInput.isNotBlank()
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpToPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Bookmarks",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val result = viewModel.toggleBookmarkForCurrentPage()
                            val message = result.fold(
                                onSuccess = { added ->
                                    if (added) "Bookmark added." else "Bookmark removed."
                                },
                                onFailure = { error ->
                                    error.message ?: "Failed to update bookmark."
                                }
                            )
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isCurrentPageBookmarked) "Remove Current Page Bookmark" else "Bookmark Current Page"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (bookmarks.isEmpty()) {
                    Text(
                        text = "No bookmarks yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    bookmarks.forEach { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.title) },
                            supportingContent = { Text("Page ${bookmark.pageNumber + 1}") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                        TextButton(
                            onClick = {
                                showBookmarksSheet = false
                                viewModel.goToPage(bookmark.pageNumber)
                            }
                        ) {
                            Text("Open Page ${bookmark.pageNumber + 1}")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AcrobatBottomToolbar(
    currentPage: Int,
    totalPages: Int,
    currentZoom: Float,
    isCurrentPageBookmarked: Boolean,
    chrome: AcrobatChrome,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Surface(color = chrome.controlSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentPage > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous page",
                    tint = chrome.onControlSurface
                )
            }
            IconButton(
                onClick = onNext,
                enabled = currentPage < totalPages - 1
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next page",
                    tint = chrome.onControlSurface
                )
            }
            Text(
                text = "${(currentZoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = chrome.onControlSurface
            )
            IconButton(onClick = onZoomOut) {
                Text(
                    text = "-",
                    color = chrome.onControlSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            IconButton(onClick = onZoomIn) {
                Text(
                    text = "+",
                    color = chrome.onControlSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Toggle bookmark",
                    tint = if (isCurrentPageBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        chrome.onControlSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinuousPdfView(
    totalPages: Int,
    currentZoom: Float,
    displayMode: DisplayMode,
    chrome: AcrobatChrome,
    currentPage: Int,
    onZoomChange: (Float) -> Unit,
    onPageVisible: (Int) -> Unit,
    loadBitmap: suspend (Int, Float) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var liveZoom by remember { mutableFloatStateOf(currentZoom) }
    var committedZoomStep by remember { mutableIntStateOf((currentZoom * 10f).roundToInt()) }

    LaunchedEffect(currentZoom) {
        liveZoom = currentZoom
        committedZoomStep = (currentZoom * 10f).roundToInt()
    }

    LaunchedEffect(currentPage) {
        if (currentPage >= 0 && currentPage < totalPages) {
            if (listState.firstVisibleItemIndex != currentPage) {
                listState.scrollToItem(currentPage)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { index ->
                if (index in 0 until totalPages) {
                    onPageVisible(index)
                }
            }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(liveZoom) {
                    detectTapGestures(
                        onDoubleTap = {
                            val target = if (liveZoom < 1.6f) 2f else 1f
                            liveZoom = target
                            committedZoomStep = (target * 10f).roundToInt()
                            onZoomChange(target)
                        }
                    )
                }
                .pointerInput(liveZoom) {
                    detectTransformGestures { _, _, zoom, _ ->
                        liveZoom = (liveZoom * zoom).coerceIn(0.5f, 3f)
                        val step = (liveZoom * 10f).roundToInt()
                        if (step != committedZoomStep) {
                            committedZoomStep = step
                            onZoomChange(step / 10f)
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(totalPages) { page ->
                val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page, key2 = currentZoom) {
                    value = loadBitmap(page, currentZoom)
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val pageWidth = maxWidth * liveZoom
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Page ${page + 1}",
                            modifier = Modifier
                                .requiredWidth(pageWidth)
                                .align(Alignment.Center),
                            colorFilter = displayMode.toColorFilter()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .requiredWidth(pageWidth)
                                .height(240.dp)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // No floating page indicator controls in continuous mode.
    }
}

@Composable
private fun SearchResultBar(
    resultText: String,
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val highlighted = remember(resultText) {
                buildAnnotatedString {
                    pushStyle(
                        SpanStyle(
                            background = Color(0xFFFFEB3B),
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    append(resultText.ifBlank { "Match" })
                    pop()
                }
            }
            Text(text = highlighted, maxLines = 1)
            Text("$currentIndex/$total")
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next match")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPageView(
    bitmap: Bitmap,
    zoomLevel: Float,
    displayMode: DisplayMode,
    onZoomChange: (Float) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(zoomLevel) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showZoomControls by remember { mutableStateOf(false) }
    var committedZoomStep by remember { mutableIntStateOf((zoomLevel * 10f).roundToInt()) }
    val density = LocalDensity.current

    LaunchedEffect(zoomLevel) {
        scale = zoomLevel
        committedZoomStep = (zoomLevel * 10f).roundToInt()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        var horizontalDragTotal by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            val target = if (scale < 1.6f) 2f else 1f
                            scale = target
                            offsetX = 0f
                            offsetY = 0f
                            onZoomChange(target)
                        }
                    )
                }
                .pointerInput(scale) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (scale <= 1.1f) {
                                horizontalDragTotal += dragAmount
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (scale <= 1.1f) {
                                when {
                                    horizontalDragTotal > 120f -> onPreviousPage()
                                    horizontalDragTotal < -120f -> onNextPage()
                                }
                            }
                            horizontalDragTotal = 0f
                        }
                    )
                }
                    .pointerInput(scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offsetX += pan.x
                            offsetY += pan.y
                            val step = (scale * 10f).roundToInt()
                            if (step != committedZoomStep) {
                                committedZoomStep = step
                                onZoomChange((step / 10f).coerceIn(0.5f, 3f))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                colorFilter = displayMode.toColorFilter()
            )

            ElevatedAssistChip(
                onClick = { showZoomControls = !showZoomControls },
                label = { Text("${(scale * 100).toInt()}%") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }

        if (showZoomControls) {
            ModalBottomSheet(
                onDismissRequest = { showZoomControls = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Zoom Controls", style = MaterialTheme.typography.titleLarge)
                    Text("Current: ${(scale * 100).toInt()}%")
                    Slider(
                        value = scale,
                        onValueChange = { newScale ->
                            scale = newScale.coerceIn(0.5f, 3f)
                        },
                        onValueChangeFinished = {
                            committedZoomStep = (scale * 10f).roundToInt()
                            onZoomChange(scale)
                        },
                        valueRange = 0.5f..3f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.75f, 1f, 1.5f, 2f).forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    scale = preset
                                    committedZoomStep = (preset * 10f).roundToInt()
                                    onZoomChange(preset)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${(preset * 100).toInt()}%")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val target = (containerWidthPx / bitmap.width.toFloat()).coerceIn(0.5f, 3f)
                                scale = target
                                committedZoomStep = (target * 10f).roundToInt()
                                offsetX = 0f
                                offsetY = 0f
                                onZoomChange(target)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fit Width")
                        }
                        Button(
                            onClick = {
                                val target = minOf(
                                    containerWidthPx / bitmap.width.toFloat(),
                                    containerHeightPx / bitmap.height.toFloat()
                                ).coerceIn(0.5f, 3f)
                                scale = target
                                committedZoomStep = (target * 10f).roundToInt()
                                offsetX = 0f
                                offsetY = 0f
                                onZoomChange(target)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fit Page")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun DisplayMode.toColorFilter(): ColorFilter? {
    return when (this) {
        DisplayMode.LIGHT, DisplayMode.READING -> null
        DisplayMode.DARK -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        DisplayMode.SEPIA -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
