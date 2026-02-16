package com.paperwise.pdf

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LRU cache for rendered PDF pages.
 * Caches bitmaps to improve scrolling performance.
 */
@Singleton
class PdfPageCache @Inject constructor() {
    companion object {
        private const val TAG = "PdfPageCache"
    }

    private data class CacheKey(
        val filePath: String,
        val pageNumber: Int,
        val zoom: Float
    )

    // Calculate cache size based on available memory
    private val maxCacheSize = calculateMemoryCacheSize()
    private val cache = object : LruCache<CacheKey, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: CacheKey, bitmap: Bitmap): Int {
            // Return the size in bytes (width * height * 4 bytes per pixel for ARGB_8888)
            return bitmap.rowBytes * bitmap.height
        }
    }
    private val mutex = Mutex()

    /**
     * Calculate cache size based on available memory.
     * Use up to 10% of available heap space for caching.
     */
    private fun calculateMemoryCacheSize(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory().toInt()
        return (maxMemory * 0.10).toInt().coerceAtLeast(8 * 1024 * 1024)
    }

    /**
     * Get a cached page bitmap.
     */
    suspend fun get(filePath: String, pageNumber: Int, zoom: Float): Bitmap? {
        return mutex.withLock {
            val key = CacheKey(filePath, pageNumber, zoom)
            cache.get(key)
        }
    }

    /**
     * Put a page bitmap in the cache.
     */
    suspend fun put(filePath: String, pageNumber: Int, zoom: Float, bitmap: Bitmap) {
        mutex.withLock {
            val key = CacheKey(filePath, pageNumber, zoom)
            cache.put(key, bitmap)
        }
    }

    /**
     * Clear all cached pages.
     */
    suspend fun clear() {
        mutex.withLock {
            cache.evictAll()
        }
    }

    /**
     * Clear cache for a specific document.
     */
    suspend fun clearDocument(filePath: String) {
        mutex.withLock {
            val keysToRemove = mutableListOf<CacheKey>()

            // Find all keys for this document
            cache.snapshot().keys.forEach { key ->
                if (key.filePath == filePath) {
                    keysToRemove.add(key)
                }
            }

            // Remove all cache entries for the document
            keysToRemove.forEach { key ->
                cache.remove(key)
            }
        }
    }

    /**
     * Get current cache size in bytes.
     */
    fun size(): Int = cache.size()

    /**
     * Get configured max cache size in bytes.
     */
    fun maxSizeInBytes(): Int = cache.maxSize()

    /**
     * Check if a page is cached.
     */
    suspend fun contains(filePath: String, pageNumber: Int, zoom: Float): Boolean {
        return mutex.withLock {
            val key = CacheKey(filePath, pageNumber, zoom)
            cache.get(key) != null
        }
    }
    
    /**
     * Preload adjacent pages to improve scrolling performance.
     */
    suspend fun preloadAdjacentPages(
        filePath: String,
        currentPage: Int,
        totalPages: Int,
        zoom: Float,
        renderer: PdfRenderer,
        preloadCount: Int = 2
    ) {
        // Preload next pages
        for (i in 1..preloadCount) {
            val nextPage = currentPage + i
            if (nextPage >= 0 && nextPage < totalPages) {
                val key = CacheKey(filePath, nextPage, zoom)

                // Check if page is already cached
                val alreadyCached = mutex.withLock { cache.get(key) != null }
                if (!alreadyCached) {
                    try {
                        val bitmap = renderer.renderPage(nextPage, zoom)
                        if (bitmap != null) {
                            mutex.withLock {
                                cache.put(key, bitmap)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to preload page $nextPage", e)
                    }
                }
            }
        }

        // Preload previous pages
        for (i in 1..preloadCount) {
            val prevPage = currentPage - i
            if (prevPage >= 0 && prevPage < totalPages) {
                val key = CacheKey(filePath, prevPage, zoom)

                // Check if page is already cached
                val alreadyCached = mutex.withLock { cache.get(key) != null }
                if (!alreadyCached) {
                    try {
                        val bitmap = renderer.renderPage(prevPage, zoom)
                        if (bitmap != null) {
                            mutex.withLock {
                                cache.put(key, bitmap)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to preload page $prevPage", e)
                    }
                }
            }
        }
    }
}
