package com.paperwise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an annotation on a PDF page.
 * Supports highlights, underlines, drawings, and comments.
 */
@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocument::class,
            parentColumns = ["filePath"],
            childColumns = ["documentPath"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentPath"), Index("pageNumber")]
)
data class Annotation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentPath: String,
    val pageNumber: Int,
    val type: AnnotationType,
    val color: Int, // ARGB color value
    val coordinates: String, // JSON string of coordinates/path
    val content: String? = null, // For comments/notes
    val createdTimestamp: Long = System.currentTimeMillis(),
    val modifiedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Types of annotations supported
 */
enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    DRAWING,
    COMMENT
}
