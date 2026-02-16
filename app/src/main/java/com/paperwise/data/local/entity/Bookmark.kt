package com.paperwise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a bookmark in a PDF document.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocument::class,
            parentColumns = ["filePath"],
            childColumns = ["documentPath"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentPath")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentPath: String,
    val pageNumber: Int,
    val title: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)
