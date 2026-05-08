# TranscribeCare ProGuard Rules

# Keep Room entities and DAOs
-keep class com.transcribecare.app.data.entity.** { *; }
-keep class com.transcribecare.app.data.dao.** { *; }

# Keep model classes (used by Room type converters or serialization)
-keep class com.transcribecare.app.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Jetpack Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep the application class
-keep class com.transcribecare.app.TranscribeCareApp { *; }
