package com.paperwise.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.paperwise.data.local.entity.PdfDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for scanning device storage for PDF files.
 */
@Singleton
class FileScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Scans the device storage for PDF files.
     * @return List of discovered PDF documents for display.
     */
    @Suppress("DEPRECATION")
    suspend fun scanForPdfFiles(): List<PdfDocument> = withContext(Dispatchers.IO) {
        val documents = mutableListOf<PdfDocument>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val addedColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    if (idColumn == -1) continue
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(collection, id.toString()).toString()
                    val rawPath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    val filePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Prefer content URIs on scoped storage devices to avoid direct file read denials.
                        contentUri
                    } else {
                        rawPath?.takeIf { it.isNotBlank() } ?: contentUri
                    }
                    val displayName = if (nameColumn != -1) cursor.getString(nameColumn) else null
                    val fileName = displayName?.takeIf { it.isNotBlank() }
                        ?: rawPath?.let { File(it).name }
                        ?: "document_$id.pdf"
                    val fileSize = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                    val modifiedSeconds = if (modifiedColumn != -1) cursor.getLong(modifiedColumn) else 0L
                    val addedSeconds = if (addedColumn != -1) cursor.getLong(addedColumn) else 0L
                    val timestamp = maxOf(modifiedSeconds, addedSeconds) * 1000L

                    documents.add(
                        PdfDocument(
                            filePath = filePath,
                            fileName = fileName,
                            fileSize = fileSize,
                            pageCount = 0,
                            lastOpenedTimestamp = timestamp
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            throw IllegalStateException(
                "Storage permission is required to scan device PDFs. Grant permission and retry.",
                e
            )
        } catch (e: IOException) {
            throw IllegalStateException("Failed to scan PDFs due to a storage I/O error.", e)
        }

        documents
            .distinctBy { it.filePath }
            .sortedByDescending { it.lastOpenedTimestamp }
    }
}
