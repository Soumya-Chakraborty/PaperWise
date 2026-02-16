package com.paperwise.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperwise.data.local.entity.PdfDocument
import com.paperwise.data.repository.PdfRepository
import com.paperwise.utils.FileScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val fileScanner: FileScanner
) : ViewModel() {
    private enum class DocumentSource {
        RECENT,
        ALL
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private var documentsJob: Job? = null
    private var activeSource: DocumentSource = DocumentSource.RECENT
    private var lastAllDocuments: List<PdfDocument> = emptyList()

    init {
        loadRecentDocuments()
    }

    fun loadRecentDocuments() {
        activeSource = DocumentSource.RECENT
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            pdfRepository.getRecentDocuments(20)
                .catch { throwable ->
                    _uiState.value = HomeUiState.Error(
                        throwable.message ?: "Unable to load recent documents."
                    )
                }
                .collect { documents ->
                    _uiState.value = if (documents.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(documents)
                    }
                }
        }
    }

    /**
     * Load all PDF files from device storage
     */
    fun loadAllPdfFiles() {
        activeSource = DocumentSource.ALL
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val scannedDocuments = runCatching { fileScanner.scanForPdfFiles() }
                .getOrElse { emptyList() }

            pdfRepository.getRecentDocuments(100)
                .catch { emit(emptyList()) }
                .combine(flowOf(scannedDocuments)) { recentDocuments, scanned ->
                    (recentDocuments + scanned)
                        .associateBy { it.filePath }
                        .values
                        .sortedByDescending { it.lastOpenedTimestamp }
                }
                .collect { documents ->
                    lastAllDocuments = documents
                    _uiState.value = if (documents.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(documents)
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            if (activeSource == DocumentSource.ALL) {
                loadAllPdfFiles()
            } else {
                loadRecentDocuments()
            }
        } else {
            if (activeSource == DocumentSource.ALL) {
                filterAllDocuments(query)
            } else {
                searchDocuments(query)
            }
        }
    }

    private fun filterAllDocuments(query: String) {
        val normalized = query.trim()
        val filtered = lastAllDocuments.filter {
            it.fileName.contains(normalized, ignoreCase = true)
        }
        _uiState.value = if (filtered.isEmpty()) {
            HomeUiState.Empty
        } else {
            HomeUiState.Success(filtered)
        }
    }

    private fun searchDocuments(query: String) {
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            pdfRepository.searchDocuments(query)
                .catch { throwable ->
                    _uiState.value = HomeUiState.Error(
                        throwable.message ?: "Search failed. Try again."
                    )
                }
                .collect { documents ->
                    _uiState.value = if (documents.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(documents)
                    }
                }
        }
    }

    fun deleteDocument(filePath: String) {
        viewModelScope.launch {
            try {
                pdfRepository.deleteDocument(filePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(
                    e.message ?: "Failed to delete document."
                )
            }
        }
    }
}

/**
 * UI state for Home screen.
 */
sealed class HomeUiState {
    object Loading : HomeUiState()
    object Empty : HomeUiState()
    data class Success(val documents: List<PdfDocument>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
