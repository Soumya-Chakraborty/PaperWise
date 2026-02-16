package com.paperwise.data.local.dao

import androidx.room.*
import com.paperwise.data.local.entity.Annotation
import com.paperwise.data.local.entity.AnnotationType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for annotations.
 */
@Dao
interface AnnotationDao {
    
    @Query("SELECT * FROM annotations WHERE documentPath = :documentPath AND pageNumber = :pageNumber ORDER BY createdTimestamp ASC")
    fun getAnnotationsForPage(documentPath: String, pageNumber: Int): Flow<List<Annotation>>
    
    @Query("SELECT * FROM annotations WHERE documentPath = :documentPath ORDER BY pageNumber ASC, createdTimestamp ASC")
    fun getAllAnnotationsForDocument(documentPath: String): Flow<List<Annotation>>
    
    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getAnnotation(id: Long): Annotation?
    
    @Query("SELECT * FROM annotations WHERE documentPath = :documentPath AND type = :type")
    fun getAnnotationsByType(documentPath: String, type: AnnotationType): Flow<List<Annotation>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: Annotation): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<Annotation>)
    
    @Update
    suspend fun updateAnnotation(annotation: Annotation)
    
    @Delete
    suspend fun deleteAnnotation(annotation: Annotation)
    
    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM annotations WHERE documentPath = :documentPath")
    suspend fun deleteAllForDocument(documentPath: String)
    
    @Query("DELETE FROM annotations WHERE documentPath = :documentPath AND pageNumber = :pageNumber")
    suspend fun deleteAllForPage(documentPath: String, pageNumber: Int)
}
