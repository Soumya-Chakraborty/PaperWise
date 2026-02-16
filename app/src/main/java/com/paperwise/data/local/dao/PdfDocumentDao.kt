package com.paperwise.data.local.dao

import androidx.room.*
import com.paperwise.data.local.entity.PdfDocument
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PDF documents.
 */
@Dao
interface PdfDocumentDao {
    
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllDocuments(): Flow<List<PdfDocument>>
    
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedTimestamp DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 10): Flow<List<PdfDocument>>
    
    @Query("SELECT * FROM pdf_documents WHERE filePath = :filePath")
    suspend fun getDocument(filePath: String): PdfDocument?
    
    @Query("SELECT * FROM pdf_documents WHERE fileName LIKE '%' || :query || '%' ORDER BY lastOpenedTimestamp DESC")
    fun searchDocuments(query: String): Flow<List<PdfDocument>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PdfDocument)
    
    @Update
    suspend fun updateDocument(document: PdfDocument)
    
    @Delete
    suspend fun deleteDocument(document: PdfDocument)
    
    @Query("DELETE FROM pdf_documents WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)
    
    @Query("UPDATE pdf_documents SET lastOpenedTimestamp = :timestamp, lastPageRead = :pageNumber WHERE filePath = :filePath")
    suspend fun updateLastRead(filePath: String, pageNumber: Int, timestamp: Long = System.currentTimeMillis())
}
