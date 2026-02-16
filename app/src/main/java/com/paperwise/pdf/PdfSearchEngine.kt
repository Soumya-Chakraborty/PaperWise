package com.paperwise.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search result containing page number and match coordinates.
 */
data class SearchResult(
    val pageNumber: Int,
    val matchText: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Full-text search engine for PDF documents.
 * Searches through all pages and returns matches with page numbers.
 */
@Singleton
class PdfSearchEngine @Inject constructor(
    private val pdfRenderer: PdfRenderer
) {
    companion object {
        private const val MAX_SEARCH_RESULTS = 5_000
    }
    
    /**
     * Search for a query string in the current document.
     * @param query Search query
     * @param caseSensitive Whether search should be case-sensitive
     * @return List of search results with page numbers
     */
    suspend fun search(
        query: String,
        caseSensitive: Boolean = false
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() || !pdfRenderer.isDocumentOpen()) {
            return@withContext emptyList()
        }
        
        val results = mutableListOf<SearchResult>()
        val pageCount = pdfRenderer.getPageCount()
        
        for (pageNumber in 0 until pageCount) {
            val pageText = pdfRenderer.extractPageText(pageNumber)
            val pageMatches = SearchTextMatcher.findMatches(
                pageText = pageText,
                query = normalizedQuery,
                caseSensitive = caseSensitive
            )
            for (match in pageMatches) {
                results.add(
                    SearchResult(
                        pageNumber = pageNumber,
                        matchText = match.matchText,
                        startIndex = match.startIndex,
                        endIndex = match.endIndex
                    )
                )
                if (results.size >= MAX_SEARCH_RESULTS) {
                    return@withContext results
                }
            }
        }
        
        results
    }
    
    /**
     * Search within a specific page range.
     */
    suspend fun searchInRange(
        query: String,
        startPage: Int,
        endPage: Int,
        caseSensitive: Boolean = false
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank() || !pdfRenderer.isDocumentOpen()) {
            return@withContext emptyList()
        }
        
        val results = mutableListOf<SearchResult>()
        val pageCount = pdfRenderer.getPageCount()
        
        val start = startPage.coerceAtLeast(0)
        val end = endPage.coerceAtMost(pageCount - 1)
        if (start > end) return@withContext emptyList()
        
        for (pageNumber in start..end) {
            val pageText = pdfRenderer.extractPageText(pageNumber)
            val pageMatches = SearchTextMatcher.findMatches(
                pageText = pageText,
                query = normalizedQuery,
                caseSensitive = caseSensitive
            )
            for (match in pageMatches) {
                results.add(
                    SearchResult(
                        pageNumber = pageNumber,
                        matchText = match.matchText,
                        startIndex = match.startIndex,
                        endIndex = match.endIndex
                    )
                )
                if (results.size >= MAX_SEARCH_RESULTS) {
                    return@withContext results
                }
            }
        }
        
        results
    }
}

internal object SearchTextMatcher {
    internal data class Match(
        val matchText: String,
        val startIndex: Int,
        val endIndex: Int
    )

    fun findMatches(pageText: String, query: String, caseSensitive: Boolean): List<Match> {
        if (pageText.isEmpty() || query.isEmpty()) return emptyList()

        val searchText = if (caseSensitive) pageText else pageText.lowercase()
        val searchQuery = if (caseSensitive) query else query.lowercase()
        val matches = mutableListOf<Match>()

        var startIndex = 0
        while (startIndex < searchText.length) {
            val index = searchText.indexOf(searchQuery, startIndex)
            if (index == -1) break

            matches.add(
                Match(
                    matchText = pageText.substring(index, index + query.length),
                    startIndex = index,
                    endIndex = index + query.length
                )
            )
            startIndex = index + 1
        }

        return matches
    }
}
