package com.paperwise.data.local

import androidx.room.TypeConverter
import com.paperwise.data.local.entity.AnnotationType

/**
 * Type converters for Room database.
 */
class Converters {
    
    @TypeConverter
    fun fromAnnotationType(value: AnnotationType): String {
        return value.name
    }
    
    @TypeConverter
    fun toAnnotationType(value: String): AnnotationType {
        return try {
            AnnotationType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AnnotationType.COMMENT // Default fallback
        }
    }
}
