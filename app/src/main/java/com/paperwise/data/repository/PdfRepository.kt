package com.paperwise.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.paperwise.data.local.dao.AnnotationDao
import com.paperwise.data.local.dao.BookmarkDao
import com.paperwise.data.local.dao.PdfDocumentDao
import com.paperwise.data.local.entity.Annotation
import com.paperwise.data.local.entity.Bookmark
import com.paperwise.data.local.entity.PdfDocument
import com.paperwise.pdf.PdfPageCache
import com.paperwise.pdf.PdfRenderer
import com.paperwise.pdf.PdfSearchEngine
import com.paperwise.pdf.SearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository for PDF operations.
 * Coordinates between database, PDF engine, and cache.
 */
@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfDocumentDao: PdfDocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    private val pdfRenderer: PdfRenderer,
    private val pdfPageCache: PdfPageCache,
    private val searchEngine: PdfSearchEngine
) {
    private data class ResolvedPdfSource(
        val sourceId: String,
        val rendererPath: String,
        val fileName: String,
        val fileSize: Long
    )
    
    // ========== Document Operations ==========
    
    /**
     * Open a PDF document and save metadata.
     */
    suspend fun openDocument(filePath: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val source = resolvePdfSource(filePath)
            val pageCount = pdfRenderer.openDocument(source.rendererPath)
            
            // Save/update document metadata
            val document = PdfDocument(
                filePath = source.sourceId,
                fileName = source.fileName,
                fileSize = source.fileSize,
                pageCount = pageCount,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
            pdfDocumentDao.insertDocument(document)
            
            Result.success(pageCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapOpenDocumentError(e))
        }
    }
    
    /**
     * Get recent documents.
     */
    fun getRecentDocuments(limit: Int = 10): Flow<List<PdfDocument>> {
        return pdfDocumentDao.getRecentDocuments(limit)
    }
    
    /**
     * Search documents by name.
     */
    fun searchDocuments(query: String): Flow<List<PdfDocument>> {
        return pdfDocumentDao.searchDocuments(query)
    }
    
    /**
     * Update last read position.
     */
    suspend fun updateLastRead(filePath: String, pageNumber: Int) {
        pdfDocumentDao.updateLastRead(filePath, pageNumber)
    }
    
    /**
     * Delete document from history.
     */
    suspend fun deleteDocument(filePath: String) = withContext(Dispatchers.IO) {
        pdfDocumentDao.deleteByPath(filePath)
        pdfPageCache.clearDocument(filePath)
    }
    
    // ========== Bookmark Operations ==========
    
    /**
     * Get bookmarks for current document.
     */
    fun getBookmarks(documentPath: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForDocument(documentPath)
    }
    
    /**
     * Add a bookmark.
     */
    suspend fun addBookmark(documentPath: String, pageNumber: Int, title: String): Long {
        val bookmark = Bookmark(
            documentPath = documentPath,
            pageNumber = pageNumber,
            title = title
        )
        return bookmarkDao.insertBookmark(bookmark)
    }
    
    /**
     * Delete a bookmark.
     */
    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark)
    }
    
    /**
     * Check if page is bookmarked.
     */
    suspend fun isPageBookmarked(documentPath: String, pageNumber: Int): Boolean {
        return bookmarkDao.getBookmarkForPage(documentPath, pageNumber) != null
    }
    
    // ========== Annotation Operations ==========
    
    /**
     * Get annotations for a specific page.
     */
    fun getAnnotationsForPage(documentPath: String, pageNumber: Int): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsForPage(documentPath, pageNumber)
    }
    
    /**
     * Save an annotation.
     */
    suspend fun saveAnnotation(annotation: Annotation): Long {
        return annotationDao.insertAnnotation(annotation)
    }
    
    /**
     * Delete an annotation.
     */
    suspend fun deleteAnnotation(annotation: Annotation) {
        annotationDao.deleteAnnotation(annotation)
    }
    
    // ========== Search Operations ==========
    
    /**
     * Search within the current document.
     */
    suspend fun searchInDocument(query: String, caseSensitive: Boolean = false): List<SearchResult> {
        return searchEngine.search(query, caseSensitive)
    }
    
    // ========== Renderer Access ==========
    
    fun getPdfRenderer(): PdfRenderer = pdfRenderer
    fun getPageCache(): PdfPageCache = pdfPageCache

    private fun resolvePdfSource(filePath: String): ResolvedPdfSource {
        if (filePath.startsWith("content://")) {
            val uri = filePath.toUri()
            return resolveContentUriSource(uri)
        }

        val normalizedPath = if (filePath.startsWith("file://")) {
            filePath.toUri().path
        } else {
            filePath
        } ?: throw IllegalArgumentException("Invalid file path: $filePath")

        val file = File(normalizedPath)
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("Cannot read PDF file: $filePath")
        }

        return ResolvedPdfSource(
            sourceId = filePath,
            rendererPath = normalizedPath,
            fileName = file.name,
            fileSize = file.length()
        )
    }

    private fun resolveContentUriSource(uri: Uri): ResolvedPdfSource {
        val (displayName, sizeFromProvider) = queryUriMetadata(uri)
        val safeName = sanitizeFileName(displayName ?: "document_${uri.hashCode()}.pdf")
        val cacheDir = File(context.cacheDir, "pdf_sources").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${uri.hashCode()}_$safeName")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw FileNotFoundException("Cannot open PDF URI: $uri")
        } catch (e: SecurityException) {
            cacheFile.delete()
            throw IllegalStateException(
                "Permission denied for this file. Please pick it again from the file picker.",
                e
            )
        } catch (e: FileNotFoundException) {
            cacheFile.delete()
            throw IllegalStateException("This PDF is no longer available at the selected location.", e)
        } catch (e: IOException) {
            cacheFile.delete()
            throw IllegalStateException("Failed to read PDF data. Please try another file.", e)
        } catch (e: Exception) {
            cacheFile.delete()
            throw IllegalStateException("Unable to open this PDF file.", e)
        }

        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
            throw IllegalStateException("PDF copy failed. Please retry with a different file.")
        }

        if (!cacheFile.canRead()) {
            throw IllegalStateException("App cannot read the copied PDF. Please retry.")
        }

        return ResolvedPdfSource(
            sourceId = uri.toString(),
            rendererPath = cacheFile.absolutePath,
            fileName = displayName ?: cacheFile.name,
            fileSize = sizeFromProvider.takeIf { it > 0L } ?: cacheFile.length()
        )
    }

    private fun mapOpenDocumentError(error: Throwable): Throwable {
        val message = when (error) {
            is SecurityException -> {
                "Permission denied while reading this PDF. Please reselect the file and grant access."
            }
            is FileNotFoundException -> "Selected PDF was not found."
            is IOException -> "I/O error while reading the PDF."
            is IllegalArgumentException -> error.message ?: "Invalid PDF file."
            is IllegalStateException -> error.message ?: "Unable to open PDF."
            else -> "Unable to open PDF. Please try another file."
        }
        return IllegalStateException(message, error)
    }

    private fun queryUriMetadata(uri: Uri): Pair<String?, Long> {
        var displayName: String? = null
        var size = 0L

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: SecurityException) {
            // Best-effort metadata lookup. Read errors are handled in stream copy path.
        }

        return displayName to size
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
