package com.paperwise.data.local.dao

import androidx.room.*
import com.paperwise.data.local.entity.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for bookmarks.
 */
@Dao
interface BookmarkDao {
    
    @Query("SELECT * FROM bookmarks WHERE documentPath = :documentPath ORDER BY pageNumber ASC")
    fun getBookmarksForDocument(documentPath: String): Flow<List<Bookmark>>
    
    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmark(id: Long): Bookmark?
    
    @Query("SELECT * FROM bookmarks WHERE documentPath = :documentPath AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getBookmarkForPage(documentPath: String, pageNumber: Int): Bookmark?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long
    
    @Update
    suspend fun updateBookmark(bookmark: Bookmark)
    
    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)
    
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM bookmarks WHERE documentPath = :documentPath")
    suspend fun deleteAllForDocument(documentPath: String)
}
