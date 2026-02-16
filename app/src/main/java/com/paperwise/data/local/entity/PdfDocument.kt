package com.paperwise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a PDF document in the database.
 * Tracks metadata for recently opened PDFs.
 */
@Entity(tableName = "pdf_documents")
data class PdfDocument(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val pageCount: Int,
    val lastOpenedTimestamp: Long,
    val lastPageRead: Int = 0,
    val thumbnailPath: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
