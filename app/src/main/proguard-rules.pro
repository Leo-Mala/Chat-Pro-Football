# Keep Application class and Activities
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }

# Keep Data Models, Room Entities, DAOs and JSON DTOs
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Keep ViewModels and UI States
-keep class com.example.ui.viewmodel.** { *; }
-keep class com.example.usecase.** { *; }

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.squareup.moshi.**

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabaseKt
-dontwarn androidx.room.paging.**

# Hilt / Dagger rules
-keep class com.example.MainApplication { *; }
-keep class com.example.** { *; }
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Release instrumentation is loaded into the target application process. AndroidJUnitRunner calls
# androidx.tracing.Trace before test code executes, so the target APK must retain this class even
# though production application code has no direct call site. Keep only the required tracing API;
# the Release AndroidTest APK has its own rules for its Kotlin runtime.
-keep class androidx.tracing.Trace { *; }
