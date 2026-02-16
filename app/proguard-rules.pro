# MuPDF reflection/native entry points.
-keep class com.artifex.mupdf.fitz.** { *; }

# Room schema classes.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Coroutines service loader.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
