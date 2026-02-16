package com.paperwise.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core PDF rendering engine using MuPDF library.
 * Handles PDF document loading, page rendering, and text extraction.
 */
@Singleton
class PdfRenderer @Inject constructor() {

    private var currentDocument: Document? = null
    private var currentFilePath: String? = null
    private var _pageCount: Int = 0
    private val mutex = Mutex()
    
    companion object {
        private const val MAX_BITMAP_SIZE = 3000 // Maximum width/height to prevent OutOfMemoryError
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50MB max file size to prevent memory issues
    }
    
    /**
     * Open a PDF document from file path.
     * @return Number of pages in the document
     */
    suspend fun openDocument(filePath: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeDocumentInternal()

            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                throw IllegalArgumentException("Cannot read PDF file: $filePath")
            }
            
            // Check file size to prevent loading extremely large files
            if (file.length() > MAX_FILE_SIZE) {
                throw IllegalArgumentException("PDF file too large: ${file.length() / (1024 * 1024)} MB. Maximum allowed: ${MAX_FILE_SIZE / (1024 * 1024)} MB")
            }

            try {
                currentDocument = Document.openDocument(filePath)
                currentFilePath = filePath
                _pageCount = currentDocument?.countPages() ?: 0

                // Check page count to prevent extremely large documents
                if (_pageCount > 5000) {
                    throw IllegalArgumentException("PDF document has too many pages ($_pageCount). Maximum allowed: 5000 pages.")
                }

                _pageCount
            } catch (e: CancellationException) {
                closeDocumentInternal()
                throw e
            } catch (e: Exception) {
                closeDocumentInternal()
                throw IllegalStateException("Failed to open PDF document.", e)
            }
        }
    }
    
    /**
     * Render a specific page to a Bitmap.
     * @param pageNumber Zero-based page number
     * @param zoom Zoom level (1.0 = 100%)
     * @param dpi Dots per inch for rendering quality
     * @return Rendered page as Bitmap
     */
    suspend fun renderPage(
        pageNumber: Int,
        zoom: Float = 1.0f,
        dpi: Float = 160f
    ): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val document = currentDocument ?: return@withLock null

            if (pageNumber < 0 || pageNumber >= document.countPages()) {
                return@withLock null
            }

            val page: Page = try {
                document.loadPage(pageNumber)
            } catch (e: Exception) {
                throw IllegalStateException("Unable to load page ${pageNumber + 1}.", e)
            }

            try {
                val bounds = page.bounds

                // Calculate scaling matrix
                val scale = (dpi / 72f) * zoom
                val matrix = Matrix(scale)

                // Calculate bitmap dimensions
                val width = ((bounds.x1 - bounds.x0) * scale).toInt().coerceAtLeast(1)
                val height = ((bounds.y1 - bounds.y0) * scale).toInt().coerceAtLeast(1)

                // Check if the resulting bitmap would be too large
                if (width > MAX_BITMAP_SIZE || height > MAX_BITMAP_SIZE) {
                    // Scale down to prevent OutOfMemoryError
                    val maxScaleFactor = MAX_BITMAP_SIZE.toFloat() / kotlin.math.max(width, height)
                    val adjustedScale = scale * maxScaleFactor

                    // Recalculate dimensions with adjusted scale
                    val adjustedWidth = ((bounds.x1 - bounds.x0) * adjustedScale).toInt().coerceAtLeast(1)
                    val adjustedHeight = ((bounds.y1 - bounds.y0) * adjustedScale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(adjustedWidth, adjustedHeight, Bitmap.Config.ARGB_8888)

                    var device: AndroidDrawDevice? = null
                    try {
                        val matrixAdjusted = Matrix(adjustedScale)
                        device = AndroidDrawDevice(bitmap)
                        page.run(device, matrixAdjusted, null)
                    } finally {
                        device?.close()
                    }

                    return@withLock bitmap
                }

                // Create bitmap and render
                val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)

                var device: AndroidDrawDevice? = null
                try {
                    device = AndroidDrawDevice(bitmap)
                    page.run(device, matrix, null)
                } finally {
                    device?.close()
                }

                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException("Failed to render page ${pageNumber + 1}.", e)
            } finally {
                page.destroy()
            }
        }
    }
    
    /**
     * Extract text from a specific page.
     * @param pageNumber Zero-based page number
     * @return Extracted text content
     */
    suspend fun extractPageText(pageNumber: Int): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val document = currentDocument ?: return@withLock ""
            if (pageNumber < 0 || pageNumber >= document.countPages()) {
                return@withLock ""
            }

            val page = document.loadPage(pageNumber)
            try {
                val structuredText = page.toStructuredText()
                try {
                    val blocks = structuredText.blocks ?: return@withLock ""
                    val builder = StringBuilder()
                    for (block in blocks) {
                        val lines = block.lines ?: continue
                        for (line in lines) {
                            val chars = line.chars ?: continue
                            for (textChar in chars) {
                                builder.append(textChar.c.toChar())
                            }
                            builder.append('\n')
                        }
                        if (builder.isNotEmpty()) {
                            builder.append('\n')
                        }
                    }
                    return@withLock builder.toString()
                } finally {
                    structuredText.destroy()
                }
            } finally {
                page.destroy()
            }
        }
    }
    
    /**
     * Get page dimensions.
     * @param pageNumber Zero-based page number
     * @return RectF with page bounds, or null if invalid
     */
    suspend fun getPageBounds(pageNumber: Int): RectF? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val document = currentDocument ?: return@withLock null
            
            if (pageNumber < 0 || pageNumber >= document.countPages()) {
                return@withLock null
            }
            
            val page = document.loadPage(pageNumber)
            val bounds = page.bounds
            page.destroy()
            
            RectF(bounds.x0, bounds.y0, bounds.x1, bounds.y1)
        }
    }
    
    /**
     * Get total number of pages in current document.
     * Thread-safe method that uses cached page count.
     */
    suspend fun getPageCount(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            _pageCount
        }
    }
    
    /**
     * Get current document file path.
     */
    fun getCurrentFilePath(): String? = currentFilePath
    
    /**
     * Close the current document and free resources.
     */
    suspend fun closeDocument() {
        mutex.withLock {
            closeDocumentInternal()
        }
    }
    
    private fun closeDocumentInternal() {
        currentDocument?.destroy()
        currentDocument = null
        currentFilePath = null
        _pageCount = 0
    }
    
    /**
     * Check if a document is currently open.
     */
    fun isDocumentOpen(): Boolean = currentDocument != null
}
