package com.paperwise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paperwise.data.local.dao.AnnotationDao
import com.paperwise.data.local.dao.BookmarkDao
import com.paperwise.data.local.dao.PdfDocumentDao
import com.paperwise.data.local.entity.Annotation
import com.paperwise.data.local.entity.Bookmark
import com.paperwise.data.local.entity.PdfDocument

/**
 * Main Room database for PaperWise application.
 */
@Database(
    entities = [
        PdfDocument::class,
        Bookmark::class,
        Annotation::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    
    companion object {
        const val DATABASE_NAME = "paperwise_database"
    }
}
