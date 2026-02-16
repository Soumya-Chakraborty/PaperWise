package com.paperwise.di

import android.content.Context
import androidx.room.Room
import com.paperwise.data.local.AppDatabase
import com.paperwise.data.local.dao.AnnotationDao
import com.paperwise.data.local.dao.BookmarkDao
import com.paperwise.data.local.dao.PdfDocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    
    @Provides
    fun providePdfDocumentDao(database: AppDatabase): PdfDocumentDao {
        return database.pdfDocumentDao()
    }
    
    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
    
    @Provides
    fun provideAnnotationDao(database: AppDatabase): AnnotationDao {
        return database.annotationDao()
    }
}
