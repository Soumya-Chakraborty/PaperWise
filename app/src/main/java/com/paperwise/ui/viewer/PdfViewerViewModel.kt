package com.paperwise.ui.viewer

import android.graphics.Bitmap
import com.paperwise.data.local.entity.Bookmark
import com.paperwise.pdf.SearchResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperwise.data.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for PDF Viewer screen.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val pdfRepository: PdfRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<PdfViewerUiState>(PdfViewerUiState.Loading)
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()
    
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    
    private val _zoomLevel = MutableStateFlow(1.0f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPagesState: StateFlow<Int> = _totalPages.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()
    
    private var totalPages = 0
    private var currentFilePath: String? = null
    private var loadDocumentJob: Job? = null
    private var loadPageJob: Job? = null
    private var bookmarksJob: Job? = null
    
    fun loadDocument(filePath: String) {
        loadDocumentJob?.cancel()
        loadPageJob?.cancel()
        loadDocumentJob = viewModelScope.launch {
            _uiState.value = PdfViewerUiState.Loading

            try {
                val result = pdfRepository.openDocument(filePath)
                result.onSuccess { pageCount ->
                    if (pageCount <= 0) {
                        _uiState.value = PdfViewerUiState.Error("This PDF has no pages to display.")
                        return@onSuccess
                    }
                    totalPages = pageCount
                    _totalPages.value = pageCount
                    currentFilePath = filePath
                    observeBookmarks(filePath)
                    loadPage(0)
                }.onFailure { error ->
                    clearDocumentState()
                    _uiState.value = PdfViewerUiState.Error(mapViewerError(error))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clearDocumentState()
                _uiState.value = PdfViewerUiState.Error(mapViewerError(e))
            }
        }
    }
    
    fun loadPage(pageNumber: Int) {
        if (pageNumber < 0 || pageNumber >= totalPages) return

        loadPageJob?.cancel()
        loadPageJob = viewModelScope.launch {
            try {
                val filePath = currentFilePath
                if (filePath.isNullOrBlank()) {
                    _uiState.value = PdfViewerUiState.Error("No PDF is currently loaded.")
                    return@launch
                }

                _currentPage.value = pageNumber
                refreshCurrentPageBookmarkState()
                _uiState.value = PdfViewerUiState.Loading

                // Try to get from cache first
                val cache = pdfRepository.getPageCache()
                val cachedBitmap = cache.get(filePath, pageNumber, _zoomLevel.value)

                if (cachedBitmap != null) {
                    _uiState.value = PdfViewerUiState.Success(
                        bitmap = cachedBitmap,
                        currentPage = pageNumber,
                        totalPages = totalPages
                    )
                } else {
                    // Render page on IO dispatcher to prevent blocking main thread
                    val bitmap = withContext(Dispatchers.IO) {
                        val renderer = pdfRepository.getPdfRenderer()
                        renderer.renderPage(pageNumber, _zoomLevel.value)
                    }

                    if (bitmap != null) {
                        // Store in cache on IO dispatcher
                        withContext(Dispatchers.IO) {
                            cache.put(filePath, pageNumber, _zoomLevel.value, bitmap)
                        }
                        
                        _uiState.value = PdfViewerUiState.Success(
                            bitmap = bitmap,
                            currentPage = pageNumber,
                            totalPages = totalPages
                        )
                    } else {
                        _uiState.value = PdfViewerUiState.Error("Failed to render the selected page.")
                    }
                }

                // Update last read position on IO dispatcher
                withContext(Dispatchers.IO) {
                    pdfRepository.updateLastRead(filePath, pageNumber)
                }
                
                // Preload adjacent pages in background to improve scrolling performance
                withContext(Dispatchers.IO) {
                    cache.preloadAdjacentPages(
                        filePath = filePath,
                        currentPage = pageNumber,
                        totalPages = totalPages,
                        zoom = _zoomLevel.value,
                        renderer = pdfRepository.getPdfRenderer()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PdfViewerUiState.Error(mapViewerError(e))
            }
        }
    }
    
    fun nextPage() {
        if (_currentPage.value < totalPages - 1) {
            loadPage(_currentPage.value + 1)
        }
    }
    
    fun previousPage() {
        if (_currentPage.value > 0) {
            loadPage(_currentPage.value - 1)
        }
    }
    
    fun setZoom(zoom: Float, reloadCurrentPage: Boolean = true) {
        _zoomLevel.value = zoom.coerceIn(0.5f, 3.0f)
        if (reloadCurrentPage) {
            loadPage(_currentPage.value)
        }
    }

    fun reloadCurrentPage() {
        if (totalPages > 0) {
            loadPage(_currentPage.value)
        }
    }

    fun goToFirstPage() {
        if (totalPages > 0) {
            loadPage(0)
        }
    }

    fun goToPage(pageNumber: Int) {
        loadPage(pageNumber)
    }

    fun setCurrentPage(pageNumber: Int) {
        if (pageNumber < 0 || pageNumber >= totalPages) return
        if (_currentPage.value == pageNumber) return

        _currentPage.value = pageNumber
        refreshCurrentPageBookmarkState()

        val filePath = currentFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                pdfRepository.updateLastRead(filePath, pageNumber)
            }
        }
    }

    suspend fun loadPageBitmap(pageNumber: Int, zoom: Float): Bitmap? = withContext(Dispatchers.IO) {
        val filePath = currentFilePath ?: return@withContext null
        if (pageNumber < 0 || pageNumber >= totalPages) return@withContext null

        val cache = pdfRepository.getPageCache()
        val cached = cache.get(filePath, pageNumber, zoom)
        if (cached != null) {
            return@withContext cached
        }

        val bitmap = pdfRepository.getPdfRenderer().renderPage(pageNumber, zoom)
        if (bitmap != null) {
            cache.put(filePath, pageNumber, zoom, bitmap)
        }
        bitmap
    }

    suspend fun searchFirstMatchPage(query: String): Int? = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@withContext null

        // Support direct page jumps from search (1-based page input).
        trimmedQuery.toIntOrNull()?.let { pageInput ->
            val pageIndex = pageInput - 1
            if (pageIndex in 0 until totalPages) {
                return@withContext pageIndex
            }
        }

        val results = pdfRepository.searchInDocument(trimmedQuery)
        results.firstOrNull()?.pageNumber
    }

    suspend fun performSearch(query: String, caseSensitive: Boolean): Int = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
            return@withContext 0
        }

        val results = pdfRepository.searchInDocument(trimmedQuery, caseSensitive)
        _searchResults.value = results
        if (results.isNotEmpty()) {
            _currentSearchIndex.value = 0
            loadPage(results.first().pageNumber)
        } else {
            _currentSearchIndex.value = -1
        }
        results.size
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val next = (_currentSearchIndex.value + 1).mod(results.size)
        _currentSearchIndex.value = next
        loadPage(results[next].pageNumber)
    }

    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val previous = if (_currentSearchIndex.value <= 0) results.lastIndex else _currentSearchIndex.value - 1
        _currentSearchIndex.value = previous
        loadPage(results[previous].pageNumber)
    }

    suspend fun toggleBookmarkForCurrentPage(): Result<Boolean> = withContext(Dispatchers.IO) {
        val filePath = currentFilePath ?: return@withContext Result.failure(
            IllegalStateException("No PDF is currently loaded.")
        )

        val pageNumber = _currentPage.value
        val existing = _bookmarks.value.firstOrNull { it.pageNumber == pageNumber }

        return@withContext runCatching {
            if (existing != null) {
                pdfRepository.deleteBookmark(existing)
                false
            } else {
                pdfRepository.addBookmark(
                    documentPath = filePath,
                    pageNumber = pageNumber,
                    title = "Page ${pageNumber + 1}"
                )
                true
            }
        }
    }
    
    override fun onCleared() {
        loadDocumentJob?.cancel()
        loadPageJob?.cancel()
        bookmarksJob?.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            kotlin.runCatching {
                pdfRepository.getPdfRenderer().closeDocument()
                pdfRepository.getPageCache().clear()
            }
        }
        super.onCleared()
    }

    private fun mapViewerError(error: Throwable): String {
        return error.message ?: "Unexpected PDF error. Please retry."
    }

    private fun observeBookmarks(filePath: String) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            try {
                pdfRepository.getBookmarks(filePath)
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        _bookmarks.value = list
                        refreshCurrentPageBookmarkState()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _bookmarks.value = emptyList()
                refreshCurrentPageBookmarkState()
            }
        }
    }

    private fun refreshCurrentPageBookmarkState() {
        val page = _currentPage.value
        _isCurrentPageBookmarked.value = _bookmarks.value.any { it.pageNumber == page }
    }

    private fun clearDocumentState() {
        bookmarksJob?.cancel()
        totalPages = 0
        currentFilePath = null
        _totalPages.value = 0
        _bookmarks.value = emptyList()
        _isCurrentPageBookmarked.value = false
        _searchResults.value = emptyList()
        _currentSearchIndex.value = -1
    }
}

/**
 * UI state for PDF Viewer.
 */
sealed class PdfViewerUiState {
    object Loading : PdfViewerUiState()
    data class Success(
        val bitmap: Bitmap,
        val currentPage: Int,
        val totalPages: Int
    ) : PdfViewerUiState()
    data class Error(val message: String) : PdfViewerUiState()
}
